"""Exercise a real loopback HTTP server and the local publisher; no deployment."""
import json
import os
from pathlib import Path
import socket
import subprocess
import time
import urllib.parse
import urllib.request
import sys
sys.dont_write_bytecode = True
from evidence import HERE, ROOT, hashes, write

dest = HERE / "http-and-files"
dest.mkdir(exist_ok=False)
sites = {
    "mayapur": ("23.416666666666668", "88.38333333333334", "Asia/Kolkata", "P0530", "2026-01-01"),
    "reykjavik": ("64.1466", "-21.9426", "Atlantic/Reykjavik", "P0000", "2026-06-26"),
}
sitefile = dest / "sites.tsv"
sitefile.write_text("\n".join(f"coords {name} {v[0]} {v[1]} {v[2]} {name}" for name,v in sites.items()) + "\n")
publish = ["java", "-cp", str(ROOT / "publish/build/install/publish/lib/*"),
           "org.panchang.publish.MainKt", "--sites", str(sitefile), "--year", "2026",
           "--out", str(dest / "feed")]
write(dest / "publisher-command.json", publish)
with (dest / "publisher.log").open("wb") as log:
    subprocess.run(publish, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, check=True)

with socket.socket() as probe:
    probe.bind(("127.0.0.1", 0))
    port = probe.getsockname()[1]
server_cmd = ["java", "-cp", str(ROOT / "api/build/install/api/lib/*"), "org.panchang.api.MainKt"]
write(dest / "server-command.json", dict(command=server_cmd, HOST="127.0.0.1", PORT=port))
base = f"http://127.0.0.1:{port}"
responses = []
with (dest / "server.log").open("wb") as log:
    server = subprocess.Popen(server_cmd, cwd=ROOT, env={**os.environ, "HOST":"127.0.0.1", "PORT":str(port)},
                              stdout=log, stderr=subprocess.STDOUT)
    try:
        for attempt in range(100):
            try:
                with urllib.request.urlopen(base + "/v1/health", timeout=2) as response:
                    assert response.status == 200
                break
            except OSError:
                assert server.poll() is None, "Local server exited"
                time.sleep(0.2)
        else:
            raise RuntimeError("Local server did not become ready")

        def get(name, path):
            with urllib.request.urlopen(base + path, timeout=180) as response:
                body = response.read()
                (dest / f"{name}.json").write_bytes(body)
                responses.append(dict(name=name, url=base+path, status=response.status))
                assert response.status == 200
                return json.loads(body)

        for name, (lat, lon, zone, legacy_zone, date) in sites.items():
            query = urllib.parse.urlencode(dict(lat=lat, lon=lon, tz=zone))
            yearly = get(name + "-year", "/v1/calendar/iskcon/2026?" + query)["ekadashiYear"]
            daily = get(name + "-day", f"/v1/day/iskcon/{date}?" + query)["ekadashiYear"]
            file = json.loads((dest / f"feed/v1/{name}/ekadashi-year.json").read_text(encoding="utf-8"))
            assert yearly == file, f"{name} API/file mismatch"
            obs = daily["observances"]
            assert len(obs) == 1
            assert obs == [o for o in yearly["observances"] if o["date"] == date or o.get("parana",{}).get("date") == date]
            assert obs[0]["parana"]["endReason"] == "ONE_THIRD_DAYLIGHT"
            legacy = json.loads((dest / f"feed/legacy/{legacy_zone}/{name}.json").read_text(encoding="utf-8"))
            assert len({d["date"] for d in legacy}) == len(legacy)
            assert len([d for d in legacy if d["date"].startswith("2026-")]) == 365
            parana_day = next(d for d in legacy if d["date"] == date)
            titles = [e["title"] for e in parana_day["events"] if e["title"].startswith("Break fast ")]
            assert len(titles) == 1
            if name == "mayapur":
                assert obs[0]["date"] == "2025-12-31"
                assert legacy[0]["date"] == "2025-12-31"
                assert any(e["title"].startswith("+") and "Ekadashi" in e["title"] for e in legacy[0]["events"])
                after = get(name + "-january-2", "/v1/day/iskcon/2026-01-02?" + query)
                assert after["ekadashiYear"]["observances"] == []
            else:
                assert obs[0]["parana"]["end"]["local"].startswith("2026-06-26T09:59:36")
                assert titles[0].endswith(" - 09:59")
        write(dest / "checks.json", dict(passed=True, requests=responses,
              checks=["real HTTP day/year agreement", "HTTP/written v1 agreement",
                      "legacy January attachment context", "no duplicated or unrelated legacy dates",
                      "Reykjavik end reason and inward legacy rounding"],
              deployed=False, junitTestCounts=False))
        print("Real loopback HTTP and written-file checks passed", flush=True)
    finally:
        server.terminate()
        server.wait(timeout=20)
write(dest / "SHA256SUMS.json", hashes(p for p in dest.rglob("*") if p.name != "SHA256SUMS.json"))
