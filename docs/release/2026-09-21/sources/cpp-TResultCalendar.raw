// level_5_days.cpp: implementation of the TResultCalendar class.
//
//////////////////////////////////////////////////////////////////////
// PORTABLE 

#include "stdafx.h"
#include "TResultCalendar.h"
#include "GCMath.h"
#include "customevent.h"
#include "enums.h"
#include "GCStrings.h"
#include "GCTithi.h"
#include "GCNaksatra.h"
#include "GCSankranti.h"
#include "GCMoonData.h"
#include "GCDisplaySettings.h"
#include "TTimeZone.h"
#include "GCAyanamsha.h"
#include "TFileXml.h"
#include "GCStringBuilder.h"
#include "GCLayoutData.h"
#include "GCUserInterface.h"
#include "GCGlobal.h"

/*

   Complete calculation of Vaisnava Calendar

Main func is TResultCalendar::CalculateCalendar

*/

//////////////////////////////////////////////////////////////////////
// Construction/Destruction
//////////////////////////////////////////////////////////////////////

TResultCalendar::TResultCalendar()
{
	nTop = 0;
	nBeg = 0;
	m_pData = NULL;
	m_PureCount = 0;
	m_nCount = 0;
	updateCalculationProgress = true;
}

TResultCalendar::~TResultCalendar()
{
	// dealokacia pola
	if (m_pData != NULL)
	{
		delete [] m_pData;
	}
}

int TResultCalendar::DAYS_TO_ENDWEEK(int lastMonthDay)
{
	return (21-(lastMonthDay - GCDisplaySettings::getValue(GENERAL_FIRST_DOW)))%7;
}

int TResultCalendar::DAYS_FROM_BEGINWEEK(int firstMonthDay)
{
	return (firstMonthDay-GCDisplaySettings::getValue(GENERAL_FIRST_DOW)+14)%7;
}

int TResultCalendar::DAY_INDEX(int day)
{
	return (day + GCDisplaySettings::getValue(GENERAL_FIRST_DOW))%7;
}

bool TResultCalendar::NextNewFullIsVriddhi(int nIndex, EARTHDATA earth)
{
	int i = 0;
	int nTithi;
	int nPrevTithi = 100;

	for(i = 0; i < BEFORE_DAYS; i++)
	{
		nTithi = m_pData[nIndex].astrodata.nTithi;
		if ((nTithi == nPrevTithi) && GCTithi::TITHI_FULLNEW_MOON(nTithi))
		{
			return TRUE;
		}
		nPrevTithi = nTithi;
		nIndex++;
	}

	return FALSE;
}

// test for MAHADVADASI 5 TO 8

bool TResultCalendar::IsMhd58(int nIndex, int &nMahaType)
{
	VAISNAVADAY & t = m_pData[nIndex];
	VAISNAVADAY & u = m_pData[nIndex + 1];

	nMahaType = NULL;

	if (t.astrodata.nNaksatra != u.astrodata.nNaksatra)
		return FALSE;

	if (t.astrodata.nPaksa != 1)
		return FALSE;

	if (t.astrodata.nTithi == t.astrodata.nTithiSunset)
	{
		if (t.astrodata.nNaksatra == 6) // punarvasu
		{
			nMahaType = EV_JAYA;
			return TRUE;
		}
		else if (t.astrodata.nNaksatra == 3) // rohini
		{
			nMahaType = EV_JAYANTI;
			return TRUE;
		}
		else if (t.astrodata.nNaksatra == 7) // pusyami
		{
			nMahaType = EV_PAPA_NASINI;
			return TRUE;
		}
		else if (t.astrodata.nNaksatra == 21) // sravana
		{
			nMahaType = EV_VIJAYA;
			return TRUE;
		}
		else
			return FALSE;
	}
	else
	{
		if (t.astrodata.nNaksatra == 21) // sravana
		{
			nMahaType = EV_VIJAYA;
			return TRUE;
		}
	}

	return FALSE;
}


/******************************************************************************************/
/* Main fucntion for VCAL calculations                                                    */
/*                                                                                        */
/*                                                                                        */
/******************************************************************************************/


int TResultCalendar::CalculateCalendar(CLocationRef & loc, VCTIME begDate, int iCount)
{
	int i, weekday;
	int nTotalCount = BEFORE_DAYS + iCount + BEFORE_DAYS;
	VCTIME date;
	EARTHDATA earth;
	int prev_paksa = 0;
	int lastMasa = 0;
	int lastGYear = 0;
	TString tempStr;
	bool bCalcMoon = (GCDisplaySettings::getValue(4) > 0 || GCDisplaySettings::getValue(5) > 0);
	bool bCalcMasa[] = 
		{ TRUE, TRUE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, TRUE, 
		TRUE, TRUE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE, TRUE };

	m_nCount = 0;
	m_Location.Set(loc);
	m_vcStart.Set(begDate);
	m_vcCount = iCount;
	earth = (EARTHDATA)loc;

	// dealokacia pola
	if (m_pData != NULL)
	{
		delete [] m_pData;
	}

	// alokacia pola
	m_pData = new VAISNAVADAY[ nTotalCount ];
	if (m_pData == NULL)
		return 0;

	// inicializacia poctovych premennych
	m_nCount = nTotalCount;
	m_PureCount = iCount;

	date.Set(begDate);
	date.shour = 0.0;
	date.tzone = loc.m_fTimezone;
//	date -= BEFORE_DAYS;

	for(i = BEFORE_DAYS; i > 0; i--)
	{
		date.PreviousDay();
	}

	weekday = (int(date.GetJulian()) + 1) % 7;

	// 1
	// initialization of days
	for(i = 0; i < nTotalCount; i++)
	{
		m_pData[i].date.Set(date);
		m_pData[i].date.dayOfWeek = weekday;
		date.NextDay();
		weekday = (weekday + 1) % 7;
		m_pData[i].moonrise.SetValue(-1);
		m_pData[i].moonset.SetValue(-1);
	}

	// 2
	// calculating moon times
	for(i = 0; i < nTotalCount; i++)
	{
		m_pData[i].nDST = TTimeZone::determineDaylightStatus(m_pData[i].date, loc.m_nDST);
//		TRACE("DST %d.%d.%d = %d\n", m_pData[i].date.day, m_pData[i].date.month, m_pData[i].date.year, m_pData[i].nDST);
	}

	// 3
	if (bCalcMoon)
	{
		for(i = 0; i < nTotalCount; i++)
		{
			if (updateCalculationProgress)
			{
				GCUserInterface::SetProgressWindowPos( (0 + 85 * i / (iCount + 1)) * 0.908 );
			}

			MOONDATA::CalcMoonTimes(earth, m_pData[i].date, double(m_pData[i].nDST), m_pData[i].moonrise, m_pData[i].moonset);

			if (GCDisplaySettings::getValue(CAL_MOON_RISE) && m_pData[i].moonrise.hour >= 0)
			{
				tempStr.Format("%s %d:%02d (%s)", GCStrings::getString(53).c_str(), m_pData[i].moonrise.hour
					, m_pData[i].moonrise.min, GCStrings::GetDSTSignature(m_pData[i].nDST));
				m_pData[i].AddEvent(PRIO_MOON, CAL_MOON_RISE, tempStr.c_str());
			}

			if (GCDisplaySettings::getValue(CAL_MOON_SET) && m_pData[i].moonset.hour >= 0)
			{
				tempStr.Format("%s %d:%02d (%s)", GCStrings::getString(54).c_str(), m_pData[i].moonset.hour
					, m_pData[i].moonset.min, GCStrings::GetDSTSignature(m_pData[i].nDST));
				m_pData[i].AddEvent(PRIO_MOON, CAL_MOON_SET, tempStr.c_str());
			}
		}
	}

	// 4
	// init of astro data
	for(i = 0; i < nTotalCount; i++)
	{
		if (updateCalculationProgress)
		{
			if (bCalcMoon)
			{
				GCUserInterface::SetProgressWindowPos( (85 + 2 * i / nTotalCount) * 0.908);
			}
			else
			{
				GCUserInterface::SetProgressWindowPos( 0.588 * 14.8 * i / nTotalCount );
			}
		}
		m_pData[i].astrodata.DayCalc(m_pData[i].date, earth);

	}

	bool calc_masa;

	// 5
	// init of masa
	prev_paksa = -1;

	for(i = 0; i < nTotalCount; i++)
	{
		calc_masa = (m_pData[i].astrodata.nPaksa != prev_paksa);
		prev_paksa = m_pData[i].astrodata.nPaksa;
		
		if (updateCalculationProgress)
		{
			if (bCalcMoon)
			{
				GCUserInterface::SetProgressWindowPos( (87 + 2 * i / nTotalCount) * 0.908 );
			}
			else
			{
				GCUserInterface::SetProgressWindowPos( 0.588 * (14.8 + 32.2 * i / nTotalCount));
			}
		}

		if (i == 0)
			calc_masa = TRUE;

		if (calc_masa)
		{
			m_pData[i].astrodata.MasaCalc(m_pData[i].date, earth);
			lastMasa = m_pData[i].astrodata.nMasa;
			lastGYear = m_pData[i].astrodata.nGaurabdaYear;
		}
		m_pData[i].astrodata.nMasa = lastMasa;
		m_pData[i].astrodata.nGaurabdaYear = lastGYear;

		if (GCDisplaySettings::getValue(CAL_ARUN_TITHI))
		{
			tempStr.Format("%s: %s", GCStrings::getString(98).c_str(), GCStrings::GetTithiName(m_pData[i].astrodata.nTithiArunodaya));
			m_pData[i].AddEvent(PRIO_ARUN, CAL_ARUN_TITHI, tempStr.c_str());
		}

		if (GCDisplaySettings::getValue(CAL_ARUN_TIME))
		{
			tempStr.Format("%s %d:%02d (%s)", GCStrings::getString(99).c_str(), m_pData[i].astrodata.sun.arunodaya.hour
				, m_pData[i].astrodata.sun.arunodaya.min, GCStrings::GetDSTSignature(m_pData[i].nDST));
			m_pData[i].AddEvent(PRIO_ARUN, CAL_ARUN_TIME, tempStr.c_str());
		}

		if (GCDisplaySettings::getValue(CAL_SUN_RISE))
		{
			tempStr.Format("%s %d:%02d (%s)", GCStrings::getString(51).c_str(), m_pData[i].astrodata.sun.rise.hour
				, m_pData[i].astrodata.sun.rise.min, GCStrings::GetDSTSignature(m_pData[i].nDST));
			m_pData[i].AddEvent(PRIO_SUN, CAL_SUN_RISE, tempStr.c_str());
		}

		if (GCDisplaySettings::getValue(CAL_SUN_NOON))
		{
			tempStr.Format("%s %d:%02d (%s)", GCStrings::getString(857).c_str(), m_pData[i].astrodata.sun.noon.hour
				, m_pData[i].astrodata.sun.noon.min, GCStrings::GetDSTSignature(m_pData[i].nDST));
			m_pData[i].AddEvent(PRIO_SUN, CAL_SUN_NOON, tempStr.c_str());
		}

		if (GCDisplaySettings::getValue(CAL_SUN_SET))
		{
			tempStr.Format("%s %d:%02d (%s)", GCStrings::getString(52).c_str(), m_pData[i].astrodata.sun.set.hour
				, m_pData[i].astrodata.sun.set.min, GCStrings::GetDSTSignature(m_pData[i].nDST));
			m_pData[i].AddEvent(PRIO_SUN, CAL_SUN_SET, tempStr.c_str());
		}

		if (GCDisplaySettings::getValue(CAL_SUN_LONG))
		{
			tempStr.Format("%s: %f (*)", GCStrings::getString(100).c_str(), m_pData[i].astrodata.sun.longitude_deg);
			m_pData[i].AddEvent(PRIO_ASTRO, CAL_SUN_LONG, tempStr.c_str());
		}

		if (GCDisplaySettings::getValue(CAL_MOON_LONG))
		{
			tempStr.Format("%s: %f (*)", GCStrings::getString(101).c_str(), m_pData[i].astrodata.moon.longitude_deg);
			m_pData[i].AddEvent(PRIO_ASTRO, CAL_MOON_LONG, tempStr.c_str());
		}

		if (GCDisplaySettings::getValue(CAL_AYANAMSHA))
		{
			tempStr.Format("%s %f (%s) (*)", GCStrings::getString(102).c_str(), m_pData[i].astrodata.msAyanamsa, GCAyanamsha::GetAyanamsaName(GCAyanamsha::GetAyanamsaType()));
			m_pData[i].AddEvent(PRIO_ASTRO, CAL_AYANAMSHA, tempStr.c_str());
		}

		if (GCDisplaySettings::getValue(CAL_JULIAN))
		{
			tempStr.Format("%s %f (*)", GCStrings::getString(103).c_str(), m_pData[i].astrodata.jdate);
			m_pData[i].AddEvent(PRIO_ASTRO, CAL_JULIAN, tempStr.c_str());
		}
	}


	if (GCDisplaySettings::getValue(CAL_MASA_CHANGE))
	{
		TString str;

		for(i = BEFORE_DAYS; i < m_PureCount + BEFORE_DAYS + 2; i++)
		{
			if (m_pData[i-1].astrodata.nMasa != m_pData[i].astrodata.nMasa)
			{
				str.Format("%s %s %s", GCStrings::getString(780).c_str(), GCStrings::GetMasaName(m_pData[i].astrodata.nMasa), GCStrings::getString(22).c_str());
				m_pData[i].AddEvent(PRIO_MASA_CHANGE, CAL_MASA_CHANGE, str.c_str());
			}

			if (m_pData[i+1].astrodata.nMasa != m_pData[i].astrodata.nMasa)
			{
				str.Format("%s %s %s", GCStrings::getString(781).c_str(), GCStrings::GetMasaName(m_pData[i].astrodata.nMasa), GCStrings::getString(22).c_str());
				m_pData[i].AddEvent(PRIO_MASA_CHANGE, CAL_MASA_CHANGE, str.c_str());
			}
		}
	}

	if (GCDisplaySettings::getValue(CAL_DST_CHANGE))
	{
		for(i = BEFORE_DAYS; i < m_PureCount + BEFORE_DAYS + 2; i++)
		{
			if (m_pData[i-1].nDST == 0 && m_pData[i].nDST==1)
				m_pData[i].AddEvent(PRIO_DST_CHANGE, CAL_DST_CHANGE, GCStrings::getString(855).c_str());
			else if (m_pData[i].nDST==1 && m_pData[i+1].nDST==0)
				m_pData[i].AddEvent(PRIO_DST_CHANGE, CAL_DST_CHANGE, GCStrings::getString(856).c_str());
		}
	}

	// 6
	// init of mahadvadasis
	for(i = 2; i < m_PureCount + BEFORE_DAYS + 3; i++)
	{
		m_pData[i].Clear();
		MahadvadasiCalc(i, earth);
	}

	// 6,5
	// init for Ekadasis
	for(i = 3; i < m_PureCount + BEFORE_DAYS + 3; i++)
	{
		if (updateCalculationProgress)
		{
			if (bCalcMoon)
			{
				GCUserInterface::SetProgressWindowPos( (89 + 5 * i / nTotalCount) * 0.908 );
			}
			else
			{
				GCUserInterface::SetProgressWindowPos( 0.588 * (47.0 + 39.5 * i / nTotalCount));
			}
		}
		EkadasiCalc(i, earth);
	}

	// 7
	// init of festivals
	for(i = BEFORE_DAYS; i < m_PureCount + BEFORE_DAYS + 3; i++)
	{
		CompleteCalc(i, earth);
	}

	// 8
	// init of festivals
	for(i = BEFORE_DAYS; i < m_PureCount + BEFORE_DAYS; i++)
	{
		ExtendedCalc(i, earth);
	}

	// resolve festivals fasting
	for(i = BEFORE_DAYS; i < m_PureCount + BEFORE_DAYS; i++)
	{
		if (m_pData[i].eparana_time1 > 0.0)
		{
			m_pData[i].eparana_time1 += m_pData[i].nDST;
		}

		if (m_pData[i].eparana_time2 > 0.0)
		{
			m_pData[i].eparana_time2 += m_pData[i].nDST;
		}

		if (m_pData[i].astrodata.sun.longitude_deg > 0.0)
		{
			m_pData[i].astrodata.sun.rise.hour += m_pData[i].nDST;
			m_pData[i].astrodata.sun.set.hour += m_pData[i].nDST;
			m_pData[i].astrodata.sun.noon.hour += m_pData[i].nDST;
			m_pData[i].astrodata.sun.arunodaya.hour += m_pData[i].nDST;
		}

		ResolveFestivalsFasting(i);
	}

	// init for sankranti
	date.Set(m_pData[0].date);
	i = 0;
	bool bFoundSan;
	int zodiac;
	int i_target;
	do
	{
		date.Set(GCSankranti::GetNextSankranti(date, zodiac));
		date.shour += TTimeZone::determineDaylightStatus(date, loc.m_nDST)/24.0;
		date.NormalizeValues();

		bFoundSan = false;
		for(i=0;i < m_nCount-1;i++)
		{
			i_target = -1;

			switch(GCSankranti::GetSankrantiType())
			{
			case 0:
				if (date.CompareYMD(m_pData[i].date) == 0)
				{
					i_target = i;
				}
				break;
			case 1:
				if (date.CompareYMD(m_pData[i].date) == 0)
				{
					if (date.shour < m_pData[i].astrodata.sun.rise.GetDayTime())
					{
						i_target = i - 1;
					}
					else
					{
						i_target = i;
					}
				}
				break;
			case 2:
				if (date.CompareYMD(m_pData[i].date) == 0)
				{
					if (date.shour > m_pData[i].astrodata.sun.noon.GetDayTime())
					{
						i_target = i+1;
					}
					else
					{
						i_target = i;
					}
				}
				break;
			case 3:
				if (date.CompareYMD(m_pData[i].date) == 0)
				{
					if (date.shour > m_pData[i].astrodata.sun.set.GetDayTime())
					{
						i_target = i+1;
					}
					else
					{
						i_target = i;
					}
				}
				break;
			}

			if (i_target >= 0)
			{
				TString str;

				m_pData[i_target].sankranti_zodiac = zodiac;
				m_pData[i_target].sankranti_day.Set(date);

				if (GCDisplaySettings::getValue(CAL_SANKRANTI))
				{
					str.Format(" %s %s (%s %s %s %d %s, %02d:%02d %s) "
						, GCStrings::GetSankrantiName(m_pData[i_target].sankranti_zodiac)
						, GCStrings::getString(56).c_str()
						, GCStrings::getString(111).c_str(), GCStrings::GetSankrantiNameEn(m_pData[i_target].sankranti_zodiac)
						, GCStrings::getString(852).c_str()
						, m_pData[i_target].sankranti_day.day, GCStrings::GetMonthAbreviation(m_pData[i_target].sankranti_day.month)
						, m_pData[i_target].sankranti_day.GetHour(), m_pData[i_target].sankranti_day.GetMinuteRound()
						, GCStrings::GetDSTSignature(m_pData[i_target].nDST));

					GCMutableDictionary * dc = m_pData[i_target].AddEvent(PRIO_SANKRANTI, CAL_SANKRANTI, str.c_str());
					dc->setStringForKey("spec", "sankranti");
				}
				bFoundSan = true;
				break;
			}
		}
		date.NextDay();
		date.NextDay();
	}
	while(bFoundSan == true);

	// 9
	// init for festivals dependent on sankranti
	for(i = BEFORE_DAYS; i < m_PureCount + BEFORE_DAYS; i++)
	{
		if (m_pData[i].sankranti_zodiac == MAKARA_SANKRANTI)
		{
			m_pData[i].AddEvent(PRIO_FESTIVALS_5, CAL_FEST_5, GCStrings::getString(78).c_str());
		}
		else if (m_pData[i].sankranti_zodiac == MESHA_SANKRANTI)
		{
			m_pData[i].AddEvent(PRIO_FESTIVALS_5, CAL_FEST_5, GCStrings::getString(79).c_str());
		}
		else if (m_pData[i+1].sankranti_zodiac == VRSABHA_SANKRANTI)
		{
			m_pData[i].AddEvent(PRIO_FESTIVALS_5, CAL_FEST_5, GCStrings::getString(80).c_str());
		}
	}

	// 10
	// init ksaya data
	// init of second day of vriddhi
	for(i = BEFORE_DAYS; i < m_PureCount + BEFORE_DAYS; i++)
	{
		if (updateCalculationProgress)
		{
			if (bCalcMoon)
			{
				GCUserInterface::SetProgressWindowPos( (94 + 6 * i / nTotalCount) * 0.908 );
			}
			else
			{
				GCUserInterface::SetProgressWindowPos( 0.588 * (86.5 + 13.5 * i / nTotalCount));
			}
		}

		if (m_pData[i].astrodata.nTithi == m_pData[i-1].astrodata.nTithi)
		{
			if (GCDisplaySettings::getValue(CAL_VRDDHI))
				m_pData[i].AddEvent(PRIO_KSAYA, CAL_VRDDHI, GCStrings::getString(90).c_str());
		}
		else if (m_pData[i].astrodata.nTithi != ((m_pData[i-1].astrodata.nTithi + 1)%30))
		{
			double h, m;
			TString str, str2, str3;
			VCTIME day1, d1, d2;

			day1.Set(m_pData[i].date);
			day1.shour = m_pData[i].astrodata.sun.sunrise_deg/360.0 + earth.tzone/24.0;

			GCTithi::GetPrevTithiStart(earth, day1, d2);
			day1.Set(d2);
			day1.shour -= 0.1;
			day1.NormalizeValues();
			GCTithi::GetPrevTithiStart(earth, day1, d1);

			d1.shour += (m_pData[i].nDST/24.0);
			d2.shour += (m_pData[i].nDST/24.0);

			d1.NormalizeValues();
			d2.NormalizeValues();

			// zaciatok ksaya tithi
			m = modf(d1.shour*24, &h);
			str2.Format("%d %s %02d:%02d", d1.day, GCStrings::GetMonthAbreviation(d1.month), int(h), int(m*60));

			// end of ksaya tithi
			m = modf(d2.shour*24, &h);
			str3.Format("%d %s %02d:%02d", d2.day, GCStrings::GetMonthAbreviation(d2.month), int(h), int(m*60));

			// print info
			str.Format("%s: %s -- %s %s %s (%s)", GCStrings::getString(89).c_str(), GCStrings::GetTithiName((m_pData[i].astrodata.nTithi + 29)%30), str2.c_str(), 
				GCStrings::getString(851).c_str(), str3.c_str(), GCStrings::GetDSTSignature(m_pData[i].nDST));

			m_pData[i].AddEvent(PRIO_KSAYA, CAL_KSAYA, str.c_str());

		}
	}

	for(i = BEFORE_DAYS; i < m_PureCount + BEFORE_DAYS; i++)
	{
		m_pData[i].dayEvents.SortByIntKey("prio");
	}


	return 1;

}


int TResultCalendar::EkadasiCalc(int nIndex, EARTHDATA earth)
{
	VAISNAVADAY & s = m_pData[nIndex-1];
	VAISNAVADAY & t = m_pData[nIndex];
	VAISNAVADAY & u = m_pData[nIndex+1];

	if (GCTithi::TITHI_EKADASI(t.astrodata.nTithi))
	{
		// if TAT < 11 then NOT_EKADASI
		if (GCTithi::TITHI_LESS_EKADASI(t.astrodata.nTithiArunodaya))
		{
			t.nMhdType = EV_NULL;
			t.ekadasi_vrata_name.Empty();
			t.nFastType = FAST_NULL;
		}
		else 
		{
			// else ak MD13 then MHD1 and/or 3
			if (GCTithi::TITHI_EKADASI(s.astrodata.nTithi) && GCTithi::TITHI_EKADASI(s.astrodata.nTithiArunodaya))
			{
				if (GCTithi::TITHI_TRAYODASI(u.astrodata.nTithi))
				{
					t.nMhdType = EV_UNMILANI_TRISPRSA;
					t.ekadasi_vrata_name = GCStrings::GetEkadasiName(t.astrodata.nMasa, t.astrodata.nPaksa);
					t.nFastType = FAST_EKADASI;
				}
				else
				{
					t.nMhdType = EV_UNMILANI;
					t.ekadasi_vrata_name = GCStrings::GetEkadasiName(t.astrodata.nMasa, t.astrodata.nPaksa);
					t.nFastType = FAST_EKADASI;
				}
			}
			else
			{
				if (GCTithi::TITHI_TRAYODASI(u.astrodata.nTithi))
				{
					t.nMhdType = EV_TRISPRSA;
					t.ekadasi_vrata_name = GCStrings::GetEkadasiName(t.astrodata.nMasa, t.astrodata.nPaksa);
					t.nFastType = FAST_EKADASI;
				}
				else
				{
					// else ak U je MAHADVADASI then NOT_EKADASI
					if (GCTithi::TITHI_EKADASI(u.astrodata.nTithi) || (u.nMhdType >= EV_SUDDHA))
					{
						t.nMhdType = EV_NULL;
						t.ekadasi_vrata_name.Empty();
						t.nFastType = FAST_NULL;
					}
					else if (u.nMhdType == EV_NULL)
					{
						// else suddha ekadasi
						t.nMhdType = EV_SUDDHA;
						t.ekadasi_vrata_name = GCStrings::GetEkadasiName(t.astrodata.nMasa, t.astrodata.nPaksa);
						t.nFastType = FAST_EKADASI;
					}
				}
			}
		}
	}
	// test for break fast

	if (s.nFastType == FAST_EKADASI)
	{
		double parBeg, parEnd;

		CalculateEParana(s, t, parBeg, parEnd, earth);

	}

	return 1;
}


/******************************************************************************************/
/*                                                                                        */
/*                                                                                        */
/*                                                                                        */
/******************************************************************************************/

int TResultCalendar::CompleteCalc(int nIndex, EARTHDATA earth)
{
	VAISNAVADAY & s = m_pData[nIndex-1];
	VAISNAVADAY & t = m_pData[nIndex];
	VAISNAVADAY & u = m_pData[nIndex+1];
	VAISNAVADAY & v = m_pData[nIndex+2];
	TString tmp;

	/*if (TITHI_EKADASI(t.astrodata.nTithi))
	{
		// if TAT < 11 then NOT_EKADASI
		if (TITHI_LESS_EKADASI(t.astrodata.nTithiArunodaya))
		{
			t.nMhdType = EV_NULL;
			t.ekadasi_vrata_name.Empty();
			t.nFastType = FAST_NULL;
		}
		else 
		{
			// else ak MD13 then MHD1 and/or 3
			if (TITHI_EKADASI(s.astrodata.nTithi) && TITHI_EKADASI(s.astrodata.nTithiArunodaya))
			{
				if (TITHI_TRAYODASI(u.astrodata.nTithi))
				{
					t.nMhdType = EV_UNMILANI_TRISPRSA;
					t.ekadasi_vrata_name = GetEkadasiName(t.astrodata.nMasa, t.astrodata.nPaksa);
					t.nFastType = FAST_EKADASI;
				}
				else
				{
					t.nMhdType = EV_UNMILANI;
					t.ekadasi_vrata_name = GetEkadasiName(t.astrodata.nMasa, t.astrodata.nPaksa);
					t.nFastType = FAST_EKADASI;
				}
			}
			else
			{
				if (TITHI_TRAYODASI(u.astrodata.nTithi))
				{
					t.nMhdType = EV_TRISPRSA;
					t.ekadasi_vrata_name = GetEkadasiName(t.astrodata.nMasa, t.astrodata.nPaksa);
					t.nFastType = FAST_EKADASI;
				}
				else
				{
					// else ak U je MAHADVADASI then NOT_EKADASI
					if (TITHI_EKADASI(u.astrodata.nTithi) || (u.nMhdType >= EV_SUDDHA))
					{
						t.nMhdType = EV_NULL;
						t.ekadasi_vrata_name.Empty();
						t.nFastType = FAST_NULL;
					}
					else if (u.nMhdType == EV_NULL)
					{
						// else suddha ekadasi
						t.nMhdType = EV_SUDDHA;
						t.ekadasi_vrata_name = GetEkadasiName(t.astrodata.nMasa, t.astrodata.nPaksa);
						t.nFastType = FAST_EKADASI;
					}
				}
			}
		}
	}
	// test for break fast

	if (s.nFastType == FAST_EKADASI)
	{
		double parBeg, parEnd;

		CalculateEParana(s, t, parBeg, parEnd, earth);

	}*/

	// test for Govardhan-puja
	if (t.astrodata.nMasa == DAMODARA_MASA)
	{
		if (t.astrodata.nTithi == TITHI_GAURA_PRATIPAT)
		{
			MOONDATA::CalcMoonTimes(earth, u.date, s.nDST, s.moonrise, s.moonset);
			MOONDATA::CalcMoonTimes(earth, t.date, t.nDST, t.moonrise, t.moonset);
			if (s.astrodata.nTithi == TITHI_GAURA_PRATIPAT)
			{
			}
			else if (u.astrodata.nTithi == TITHI_GAURA_PRATIPAT)
			{
				if (t.moonrise.hour >= 0)
				{
					if (t.moonrise.IsGreaterThan(t.astrodata.sun.rise))
					// today is GOVARDHANA PUJA
						t.AddSpecFestival(SPEC_GOVARDHANPUJA, CAL_FEST_1);
					else
						u.AddSpecFestival(SPEC_GOVARDHANPUJA, CAL_FEST_1);
				}
				else if (u.moonrise.hour >= 0)
				{
					if (u.moonrise.IsLessThan(u.astrodata.sun.rise))
					// today is GOVARDHANA PUJA
						t.AddSpecFestival(SPEC_GOVARDHANPUJA, CAL_FEST_1);
					else
						u.AddSpecFestival(SPEC_GOVARDHANPUJA, CAL_FEST_1);
				}
				else
				{
					t.AddSpecFestival(SPEC_GOVARDHANPUJA, CAL_FEST_1);
				}
			}
			else
			{
				// today is GOVARDHANA PUJA
				t.AddSpecFestival(SPEC_GOVARDHANPUJA, CAL_FEST_1);
			}

		}
		else if ((t.astrodata.nTithi == TITHI_GAURA_DVITIYA) && (s.astrodata.nTithi == TITHI_AMAVASYA))
		{
			// today is GOVARDHANA PUJA
			t.AddSpecFestival(SPEC_GOVARDHANPUJA, CAL_FEST_1);
		}
	}

	int mid_nak_t, mid_nak_u;

	if (t.astrodata.nMasa == HRSIKESA_MASA)
	{
		// test for Janmasthami
		if (IsFestivalDay(s, t, TITHI_KRSNA_ASTAMI))
		{
			// if next day is not astami, so that means that astami is not vriddhi
			// then today is SKJ
			if (u.astrodata.nTithi != TITHI_KRSNA_ASTAMI)
			{
				// today is Sri Krsna Janmasthami
				t.AddSpecFestival(SPEC_JANMASTAMI, CAL_FEST_0);
				u.AddSpecFestival(SPEC_NANDAUTSAVA, CAL_FEST_1);
				u.AddSpecFestival(SPEC_PRABHAPP, CAL_FEST_2);
//				t.nFastType = (GCDisplaySettings::getValue(42) ? FAST_MIDNIGHT : FAST_TODAY);
			}
			else // tithi is vriddhi and we have to test both days
			{
				// test when both days have ROHINI
				if ((t.astrodata.nNaksatra == ROHINI_NAKSATRA) && (u.astrodata.nNaksatra == ROHINI_NAKSATRA))
				{
					mid_nak_t = (int)GCNaksatra::CalculateMidnightNaksatra(t.date, earth);
					mid_nak_u = (int)GCNaksatra::CalculateMidnightNaksatra(u.date, earth);

					// test when both days have modnight naksatra ROHINI
					if ((ROHINI_NAKSATRA == mid_nak_u) && (mid_nak_t == ROHINI_NAKSATRA))
					{
						// choice day which is monday or wednesday
						if ((u.date.dayOfWeek == DW_MONDAY) || (u.date.dayOfWeek == DW_WEDNESDAY))
						{
							u.AddSpecFestival(SPEC_JANMASTAMI, CAL_FEST_0);
							v.AddSpecFestival(SPEC_NANDAUTSAVA, CAL_FEST_1);
							v.AddSpecFestival(SPEC_PRABHAPP, CAL_FEST_2);
//							u.nFastType = (GCDisplaySettings::getValue(42) ? FAST_MIDNIGHT : FAST_TODAY);
						}
						else
						{
							// today is Sri Krsna Janmasthami
							t.AddSpecFestival(SPEC_JANMASTAMI, CAL_FEST_0);
							u.AddSpecFestival(SPEC_NANDAUTSAVA, CAL_FEST_1);
							u.AddSpecFestival(SPEC_PRABHAPP, CAL_FEST_2);
//							t.nFastType = (GCDisplaySettings::getValue(42) ? FAST_MIDNIGHT : FAST_TODAY);
						}
					}
					else if (mid_nak_t == ROHINI_NAKSATRA)
					{
						// today is Sri Krsna Janmasthami
						t.AddSpecFestival(SPEC_JANMASTAMI, CAL_FEST_0);
						u.AddSpecFestival(SPEC_NANDAUTSAVA, CAL_FEST_1);
//						t.nFastType = (GCDisplaySettings::getValue(42) ? FAST_MIDNIGHT : FAST_TODAY);
						u.AddSpecFestival(SPEC_PRABHAPP, CAL_FEST_2);
					}
					else if (mid_nak_u == ROHINI_NAKSATRA)
					{
						u.AddSpecFestival(SPEC_JANMASTAMI, CAL_FEST_0);
						v.AddSpecFestival(SPEC_NANDAUTSAVA, CAL_FEST_1);
						v.AddSpecFestival(SPEC_PRABHAPP, CAL_FEST_2);
//						u.nFastType = (GCDisplaySettings::getValue(42) ? FAST_MIDNIGHT : FAST_TODAY);
					}
					else
					{
						if ((u.date.dayOfWeek == DW_MONDAY) || (u.date.dayOfWeek == DW_WEDNESDAY))
						{
							u.AddSpecFestival(SPEC_JANMASTAMI, CAL_FEST_0);
							v.AddSpecFestival(SPEC_NANDAUTSAVA, CAL_FEST_1);
							v.AddSpecFestival(SPEC_PRABHAPP, CAL_FEST_2);
//							u.nFastType = (GCDisplaySettings::getValue(42) ? FAST_MIDNIGHT : FAST_TODAY);
						}
						else
						{
							// today is Sri Krsna Janmasthami
							t.AddSpecFestival(SPEC_JANMASTAMI, CAL_FEST_0);
							u.AddSpecFestival(SPEC_NANDAUTSAVA, CAL_FEST_1);
							u.AddSpecFestival(SPEC_PRABHAPP, CAL_FEST_2);
//							t.nFastType = (GCDisplaySettings::getValue(42) ? FAST_MIDNIGHT : FAST_TODAY);
						}
					}
				}
				else if (t.astrodata.nNaksatra == ROHINI_NAKSATRA)
				{
					// today is Sri Krsna Janmasthami
					t.AddSpecFestival(SPEC_JANMASTAMI, CAL_FEST_0);
					u.AddSpecFestival(SPEC_NANDAUTSAVA, CAL_FEST_1);
					u.AddSpecFestival(SPEC_PRABHAPP, CAL_FEST_2);
//					t.nFastType = (GCDisplaySettings::getValue(42) ? FAST_MIDNIGHT : FAST_TODAY);
				}
				else if (u.astrodata.nNaksatra == ROHINI_NAKSATRA)
				{
					u.AddSpecFestival(SPEC_JANMASTAMI, CAL_FEST_0);
					v.AddSpecFestival(SPEC_NANDAUTSAVA, CAL_FEST_1);
					v.AddSpecFestival(SPEC_PRABHAPP, CAL_FEST_2);
//					u.nFastType = (GCDisplaySettings::getValue(42) ? FAST_MIDNIGHT : FAST_TODAY);
				}
				else
				{
					if ((u.date.dayOfWeek == DW_MONDAY) || (u.date.dayOfWeek == DW_WEDNESDAY))
					{
						u.AddSpecFestival(SPEC_JANMASTAMI, CAL_FEST_0);
						v.AddSpecFestival(SPEC_NANDAUTSAVA, CAL_FEST_1);
						v.AddSpecFestival(SPEC_PRABHAPP, CAL_FEST_2);
//						u.nFastType = (GCDisplaySettings::getValue(42) ? FAST_MIDNIGHT : FAST_TODAY);
					}
					else
					{
						// today is Sri Krsna Janmasthami
						t.AddSpecFestival(SPEC_JANMASTAMI, CAL_FEST_0);
						u.AddSpecFestival(SPEC_NANDAUTSAVA, CAL_FEST_1);
						u.AddSpecFestival(SPEC_PRABHAPP, CAL_FEST_2);
//						t.nFastType = (GCDisplaySettings::getValue(42) ? FAST_MIDNIGHT : FAST_TODAY);
					}
				}
			}
		}

/*		if (s.nSpecFestival == SPEC_JANMASTAMI)
		{
			// today is SP VyasaPuja
			// today is Nandottsava
			t.AddSpecFestival(SPEC_NANDOTSAVA);
//			t.nFastType = FAST_NOON;
		}
*/
		// test for Vamana Dvadasi
/*		if (IsFestivalDay(s, t, TITHI_GAURA_DVADASI))
		{
			// today is Vamana Dvadasi
			t.nSpecFestival = SPEC_VAMANADVADASI;

			if (t.nFastType == FAST_EKADASI)
			{
				t.nFeasting = FEAST_TOMMOROW_FAST_TODAY;
				u.nFeasting = FEAST_TODAY_FAST_YESTERDAY;
			}
			else
			{
				t.nFeasting = FEAST_TODAY_FAST_YESTERDAY;
			}
		}*/
	}

	// test for Varaha Dvadasi
	/*if (t.astrodata.nMasa == MADHAVA_MASA)
	{
		if (((t.astrodata.nTithi == TITHI_GAURA_DVADASI) && (s.astrodata.nTithi < TITHI_GAURA_DVADASI))
			|| ((t.astrodata.nTithi == TITHI_GAURA_EKADASI) && (u.astrodata.nTithi == TITHI_GAURA_TRAYODASI)))
		{
			// today is Varaha Dvadasi
			t.nSpecFestival = SPEC_VARAHADVADASI;

			if (t.nFastType == FAST_EKADASI)
			{
				t.nFeasting = FEAST_TOMMOROW_FAST_TODAY;
				u.nFeasting = FEAST_TODAY_FAST_YESTERDAY;
			}
			else
			{
				t.nFeasting = FEAST_TODAY_FAST_YESTERDAY;
			}
		}
	}*/

	// test for RathaYatra
	if (t.astrodata.nMasa == VAMANA_MASA)
	{
		if (IsFestivalDay(s, t, TITHI_GAURA_DVITIYA))
		{
			t.AddSpecFestival(SPEC_RATHAYATRA, CAL_FEST_1);
		}

		if (nIndex > 4)
		{
			if (IsFestivalDay(m_pData[nIndex - 5], m_pData[nIndex - 4], TITHI_GAURA_DVITIYA))
			{
				t.AddSpecFestival(SPEC_HERAPANCAMI, CAL_FEST_1);
			}
		}

		if (nIndex > 8)
		{
			if (IsFestivalDay(m_pData[nIndex - 9], m_pData[nIndex - 8], TITHI_GAURA_DVITIYA))
			{
				t.AddSpecFestival(SPEC_RETURNRATHA, CAL_FEST_1);
			}
		}

		if (IsFestivalDay(m_pData[nIndex], m_pData[nIndex + 1], TITHI_GAURA_DVITIYA))
		{
			t.AddSpecFestival(SPEC_GUNDICAMARJANA, CAL_FEST_1);
		}

	}

	// test for Gaura Purnima
	if (s.astrodata.nMasa == GOVINDA_MASA)
	{
		if (IsFestivalDay(s, t, TITHI_PURNIMA))
		{
			t.AddSpecFestival(SPEC_GAURAPURNIMA, CAL_FEST_0);
//			t.nFastType = FAST_MOONRISE;
		}
	}

	// test for Jagannatha Misra festival
	if (m_pData[nIndex-2].astrodata.nMasa == GOVINDA_MASA)
	{
		if (IsFestivalDay(m_pData[nIndex - 2], s, TITHI_PURNIMA))
		{
			t.AddSpecFestival(SPEC_MISRAFESTIVAL, CAL_FEST_1);
		}
	}


	// ------------------------
	// test for other festivals
	// ------------------------

	int n2;
	int _masa_from = -1, _masa_to = -1;
	int _tithi_from = -1, _tithi_to = -1;
	CCustomEvent * pEvx;

	TString evx_temp;

	if (t.astrodata.nMasa > 11)
		goto other_fest;

	if (s.astrodata.nTithi != t.astrodata.nTithi) {
		_tithi_to = t.astrodata.nTithi;
		_masa_to  = t.astrodata.nMasa;
	}

	// resolving 
	if (GCDisplaySettings::getValue(71) == 0) {
		// if ksaya tithi, then s2 is true
		if ((t.astrodata.nTithi != s.astrodata.nTithi) && 
			(t.astrodata.nTithi != (s.astrodata.nTithi + 1)%30))
		{
			n2 = (t.astrodata.nMasa * 30 + t.astrodata.nTithi + 359 ) % 360; // this is index into table of festivals for previous tithi
			_tithi_from   = n2 % 30;
			_masa_from    = n2 / 30;
		}
	} else {
		// if ksaya tithi, then s2 is true
		if ((u.astrodata.nTithi != t.astrodata.nTithi) && 
			(u.astrodata.nTithi != (t.astrodata.nTithi + 1)%30))
		{
			n2 = (t.astrodata.nMasa * 30 + t.astrodata.nTithi + 1) % 360; // this is index into table of festivals for previous tithi
			_tithi_from   = n2 % 30;
			_masa_from    = n2 / 30;
		}
	}

	int currFestTop = 0;
	GCMutableDictionary * md = NULL;
	int eventSelected = 0;

	for(int kn = 0; kn < CCustomEventList::Count(); kn ++)
	{
		eventSelected = 0;
		pEvx = CCustomEventList::EventAtIndex(kn);
		if (_masa_from >= 0 && pEvx->nMasa==_masa_from && pEvx->nTithi == _tithi_from && pEvx->nUsed && pEvx->nVisible) 
			eventSelected = 1;
		else if (_masa_to >= 0 && pEvx->nMasa==_masa_to && pEvx->nTithi==_tithi_to && pEvx->nUsed && pEvx->nVisible)
			eventSelected = 1;

		if (!eventSelected) continue;
		md = t.AddEvent(PRIO_FESTIVALS_0 + pEvx->nClass*100 + currFestTop, CAL_FEST_0 + pEvx->nClass,
			pEvx->strText.c_str());
		currFestTop += 5;
		if (pEvx->nFastType > 0)
		{
			md->setIntForKey("fasttype", pEvx->nFastType);
			md->setStringForKey("fastsubject", pEvx->strFastSubject.c_str());
		}

		if (GCDisplaySettings::getValue(51) != 2 && pEvx->nStartYear > -7000)
		{
			TString ss1;
			int years = t.astrodata.nGaurabdaYear - (pEvx->nStartYear - 1496);
			char * appx = "th";
			if (years % 10 == 1) appx = "st";
			else if (years % 10 == 2) appx = "nd";
			else if (years % 10 == 3) appx = "rd";
			if (GCDisplaySettings::getValue(51) == 0)
			{
				ss1.Format("%s (%d%s anniversary)", pEvx->strText.c_str(), years, appx);
			}
			else
			{
				ss1.Format("%s (%d%s)", pEvx->strText.c_str(), years, appx);
			}
			md->setStringForKey("text", ss1.c_str());
		}
	}


other_fest:
	// ---------------------------
	// bhisma pancaka test
	// ---------------------------

	if (t.astrodata.nMasa == DAMODARA_MASA)
	{
		if ((t.astrodata.nPaksa == GAURA_PAKSA) && (t.nFastType == FAST_EKADASI))
		{
			t.AddEvent(PRIO_CM_DAYNOTE, DISP_ALWAYS, GCStrings::getString(81).c_str());
			//if (t.festivals.GetLength() > 0)
			//	t.festivals += "#";
			//t.festivals += GCStrings::getString(81);
		}
	}

	// ---------------------------
	// caturmasya tests
	// ---------------------------
	
	// first month for punima and ekadasi systems
	if (t.astrodata.nMasa == VAMANA_MASA)
	{
		// purnima system
		if (GCTithi::TITHI_TRANSIT(t.astrodata.nTithi, u.astrodata.nTithi, TITHI_GAURA_CATURDASI, TITHI_PURNIMA))
		{
			if (GCDisplaySettings::getValue(CATURMASYA_PURNIMA))
			{
				tmp.Format("%s [PURNIMA SYSTEM]", GCStrings::getString(112).c_str());
				u.AddEvent(PRIO_CM_DAY, CATURMASYA_PURNIMA, tmp.c_str());
				u.AddEvent(PRIO_CM_DAYNOTE, CATURMASYA_PURNIMA, GCStrings::getString(114).c_str());
				u.nCaturmasya = CMASYA_SYSTEM_PURNIMA | CMASYA_DAY_FIRST | CMASYA_MONTH_1;
			}
		}

		// ekadasi system
		//if (TITHI_TRANSIT(s.astrodata.nTithi, t.astrodata.nTithi, TITHI_GAURA_DASAMI, TITHI_GAURA_EKADASI))
		if ((t.astrodata.nPaksa == GAURA_PAKSA) && (t.nMhdType != EV_NULL))
		{
			if (GCDisplaySettings::getValue(CATURMASYA_EKADASI))
			{
				tmp.Format("%s [EKADASI SYSTEM]", GCStrings::getString(112).c_str());
				t.AddEvent(PRIO_CM_DAY, CATURMASYA_EKADASI, tmp.c_str());
				t.AddEvent(PRIO_CM_DAYNOTE, CATURMASYA_EKADASI, GCStrings::getString(114).c_str());
				t.nCaturmasya = CMASYA_SYSTEM_EKADASI | CMASYA_DAY_FIRST | CMASYA_MONTH_1;
			}
		}
	}

	// first month for pratipat system
	// month transit for purnima and ekadasi systems
	if (t.astrodata.nMasa == SRIDHARA_MASA)
	{
		if (s.astrodata.nMasa == ADHIKA_MASA)
		{
			t.AddEvent(PRIO_CM_DAYNOTE, DISP_ALWAYS, GCStrings::getString(115).c_str());
		}

		// pratipat system
		if (GCTithi::TITHI_TRANSIT(s.astrodata.nTithi, t.astrodata.nTithi, TITHI_PURNIMA, TITHI_KRSNA_PRATIPAT))
		{
			if (GCDisplaySettings::getValue(CATURMASYA_PRATIPAT))
			{
				tmp.Format("%s [PRATIPAT SYSTEM]", GCStrings::getString(112).c_str());
				t.AddEvent(PRIO_CM_DAY, CATURMASYA_PRATIPAT, tmp.c_str());
				t.AddEvent(PRIO_CM_DAYNOTE, CATURMASYA_PRATIPAT, GCStrings::getString(114).c_str());
				t.nCaturmasya = CMASYA_SYSTEM_PRATIPAT | CMASYA_DAY_FIRST | CMASYA_MONTH_1;
			}
		}

		// first day of particular month for PURNIMA system, when purnima is not KSAYA
		if (GCTithi::TITHI_TRANSIT(t.astrodata.nTithi, u.astrodata.nTithi, TITHI_GAURA_CATURDASI, TITHI_PURNIMA))
		{
			if (GCDisplaySettings::getValue(CATURMASYA_PURNIMA))
			{
				tmp.Format("%s [PURNIMA SYSTEM]", GCStrings::getString(116).c_str());
				u.AddEvent(PRIO_CM_DAY, CATURMASYA_PURNIMA, tmp.c_str());
				u.AddEvent(PRIO_CM_DAYNOTE, CATURMASYA_PURNIMA, GCStrings::getString(118).c_str());
				u.nCaturmasya = CMASYA_SYSTEM_PURNIMA | CMASYA_DAY_FIRST | CMASYA_MONTH_2;
				tmp.Format("%s [PURNIMA SYSTEM]", GCStrings::getString(113).c_str());
				t.AddEvent(PRIO_CM_DAY, CATURMASYA_PURNIMA, tmp.c_str());
				t.nCaturmasya = CMASYA_SYSTEM_PURNIMA | CMASYA_DAY_LAST | CMASYA_MONTH_1;
			}
		}

		// ekadasi system
		//if (TITHI_TRANSIT(s.astrodata.nTithi, t.astrodata.nTithi, TITHI_GAURA_DASAMI, TITHI_GAURA_EKADASI))
		if ((t.astrodata.nPaksa == GAURA_PAKSA) && (t.nMhdType != EV_NULL))
		{
			if (GCDisplaySettings::getValue(CATURMASYA_EKADASI))
			{
				tmp.Format("%s [EKADASI SYSTEM]", GCStrings::getString(116).c_str());
				t.AddEvent(PRIO_CM_DAY, CATURMASYA_EKADASI, tmp.c_str());
				t.AddEvent(PRIO_CM_DAYNOTE, CATURMASYA_EKADASI, GCStrings::getString(118).c_str());
				t.nCaturmasya = CMASYA_SYSTEM_EKADASI | CMASYA_DAY_FIRST | CMASYA_MONTH_2;
				tmp.Format("%s [EKADASI SYSTEM]", GCStrings::getString(113).c_str());
				s.AddEvent(PRIO_CM_DAY, CATURMASYA_EKADASI, tmp.c_str());
				s.nCaturmasya = CMASYA_SYSTEM_EKADASI | CMASYA_DAY_LAST | CMASYA_MONTH_1;
			}
		}
	}

	// second month for pratipat system
	// month transit for purnima and ekadasi systems
	if (t.astrodata.nMasa == HRSIKESA_MASA)
	{
		if (s.astrodata.nMasa == ADHIKA_MASA)
		{
			t.AddEvent(PRIO_CM_DAYNOTE, DISP_ALWAYS, GCStrings::getString(119).c_str());
		}

		// pratipat system
		if (GCTithi::TITHI_TRANSIT(s.astrodata.nTithi, t.astrodata.nTithi, TITHI_PURNIMA, TITHI_KRSNA_PRATIPAT))
		{
			if (GCDisplaySettings::getValue(CATURMASYA_PRATIPAT))
			{
				tmp.Format("%s [PRATIPAT SYSTEM]", GCStrings::getString(116).c_str());
				t.AddEvent(PRIO_CM_DAY, CATURMASYA_PRATIPAT, tmp.c_str());
				t.AddEvent(PRIO_CM_DAYNOTE, CATURMASYA_PRATIPAT, GCStrings::getString(118).c_str());
				t.nCaturmasya = CMASYA_SYSTEM_PRATIPAT | CMASYA_DAY_FIRST | CMASYA_MONTH_2;
				tmp.Format("%s [PRATIPAT SYSTEM]", GCStrings::getString(113).c_str());
				s.AddEvent(PRIO_CM_DAY, CATURMASYA_PRATIPAT, tmp.c_str());
				s.nCaturmasya = CMASYA_SYSTEM_PRATIPAT | CMASYA_DAY_LAST | CMASYA_MONTH_1;
			}
		}

		// first day of particular month for PURNIMA system, when purnima is not KSAYA
		if (GCTithi::TITHI_TRANSIT(t.astrodata.nTithi, u.astrodata.nTithi, TITHI_GAURA_CATURDASI, TITHI_PURNIMA))
		{
			if (GCDisplaySettings::getValue(CATURMASYA_PURNIMA))
			{
				tmp.Format("%s [PURNIMA SYSTEM]", GCStrings::getString(120).c_str());
				u.AddEvent(PRIO_CM_DAY, CATURMASYA_PURNIMA, tmp.c_str());
				u.AddEvent(PRIO_CM_DAYNOTE, CATURMASYA_PURNIMA, GCStrings::getString(122).c_str());
				u.nCaturmasya = CMASYA_SYSTEM_PURNIMA | CMASYA_DAY_FIRST | CMASYA_MONTH_3;
				tmp.Format("%s [PURNIMA SYSTEM]", GCStrings::getString(117).c_str());
				t.AddEvent(PRIO_CM_DAY, CATURMASYA_PURNIMA, tmp.c_str());
				t.nCaturmasya = CMASYA_SYSTEM_PURNIMA | CMASYA_DAY_LAST | CMASYA_MONTH_2;
			}
		}
		// ekadasi system
		if ((t.astrodata.nPaksa == GAURA_PAKSA) && (t.nMhdType != EV_NULL))
		//if (TITHI_TRANSIT(s.astrodata.nTithi, t.astrodata.nTithi, TITHI_GAURA_DASAMI, TITHI_GAURA_EKADASI))
		{
			if (GCDisplaySettings::getValue(CATURMASYA_EKADASI))
			{
				tmp.Format("%s [EKADASI SYSTEM]", GCStrings::getString(120).c_str());
				t.AddEvent(PRIO_CM_DAY, CATURMASYA_EKADASI, tmp.c_str());
				t.AddEvent(PRIO_CM_DAYNOTE, CATURMASYA_EKADASI, GCStrings::getString(122).c_str());
				t.nCaturmasya = CMASYA_SYSTEM_EKADASI | CMASYA_DAY_FIRST | CMASYA_MONTH_3;
				tmp.Format("%s [EKADASI SYSTEM]", GCStrings::getString(117).c_str());
				s.AddEvent(PRIO_CM_DAY, CATURMASYA_EKADASI, tmp.c_str());
				s.nCaturmasya = CMASYA_SYSTEM_EKADASI | CMASYA_DAY_LAST | CMASYA_MONTH_2;
			}
		}
	}

	// third month for pratipat
	// month transit for purnima and ekadasi systems
	if (t.astrodata.nMasa == PADMANABHA_MASA)
	{
		if (s.astrodata.nMasa == ADHIKA_MASA)
		{
			t.AddEvent(PRIO_CM_DAYNOTE, DISP_ALWAYS, GCStrings::getString(123).c_str());
		}
		// pratipat system
		if (GCTithi::TITHI_TRANSIT(s.astrodata.nTithi, t.astrodata.nTithi, TITHI_PURNIMA, TITHI_KRSNA_PRATIPAT))
//		if (s.astrodata.nMasa == HRSIKESA_MASA)
		{
			if (GCDisplaySettings::getValue(CATURMASYA_PRATIPAT))
			{
				tmp.Format("%s [PRATIPAT SYSTEM]", GCStrings::getString(120).c_str());
				t.AddEvent(PRIO_CM_DAY, CATURMASYA_PRATIPAT, tmp.c_str());
				t.AddEvent(PRIO_CM_DAYNOTE, CATURMASYA_PRATIPAT, GCStrings::getString(122).c_str());
				t.nCaturmasya = CMASYA_SYSTEM_PRATIPAT | CMASYA_DAY_FIRST | CMASYA_MONTH_3;
				tmp.Format("%s [PRATIPAT SYSTEM]", GCStrings::getString(117).c_str());
				s.AddEvent(PRIO_CM_DAY, CATURMASYA_PRATIPAT, tmp.c_str());
				s.nCaturmasya = CMASYA_SYSTEM_PRATIPAT | CMASYA_DAY_LAST | CMASYA_MONTH_2;
			}
		}

		// first day of particular month for PURNIMA system, when purnima is not KSAYA
		if (GCTithi::TITHI_TRANSIT(t.astrodata.nTithi, u.astrodata.nTithi, TITHI_GAURA_CATURDASI, TITHI_PURNIMA))
		{
			if (GCDisplaySettings::getValue(CATURMASYA_PURNIMA))
			{
				tmp.Format("%s [PURNIMA SYSTEM]", GCStrings::getString(124).c_str());
				u.AddEvent(PRIO_CM_DAY, CATURMASYA_PURNIMA, tmp.c_str());
				u.AddEvent(PRIO_CM_DAYNOTE, CATURMASYA_PURNIMA, GCStrings::getString(126).c_str());
				u.nCaturmasya = CMASYA_SYSTEM_PURNIMA | CMASYA_DAY_FIRST | CMASYA_MONTH_4;
				tmp.Format("%s [PURNIMA SYSTEM]", GCStrings::getString(121).c_str());
				t.AddEvent(PRIO_CM_DAY, CATURMASYA_PURNIMA, tmp.c_str());
				t.nCaturmasya = CMASYA_SYSTEM_PURNIMA | CMASYA_DAY_LAST | CMASYA_MONTH_3;
			}
		}

		// ekadasi system
		if ((t.astrodata.nPaksa == GAURA_PAKSA) && (t.nMhdType != EV_NULL))
		//if (TITHI_TRANSIT(s.astrodata.nTithi, t.astrodata.nTithi, TITHI_GAURA_DASAMI, TITHI_GAURA_EKADASI))
		{
			if (GCDisplaySettings::getValue(CATURMASYA_EKADASI))
			{
				tmp.Format("%s [EKADASI SYSTEM]", GCStrings::getString(124).c_str());
				t.AddEvent(PRIO_CM_DAY, CATURMASYA_EKADASI, tmp.c_str());
				t.AddEvent(PRIO_CM_DAYNOTE, CATURMASYA_EKADASI, GCStrings::getString(126).c_str());
				t.nCaturmasya = CMASYA_SYSTEM_EKADASI | CMASYA_DAY_FIRST | CMASYA_MONTH_4;
				tmp.Format("%s [EKADASI SYSTEM]", GCStrings::getString(121).c_str());
				s.AddEvent(PRIO_CM_DAY, CATURMASYA_EKADASI, tmp.c_str());
				s.nCaturmasya = CMASYA_SYSTEM_EKADASI | CMASYA_DAY_LAST | CMASYA_MONTH_3;
			}
		}
	}

	// fourth month for pratipat system
	// month transit for purnima and ekadasi systems
	if (t.astrodata.nMasa == DAMODARA_MASA)
	{
		if (s.astrodata.nMasa == ADHIKA_MASA)
		{
			t.AddEvent(PRIO_CM_DAYNOTE, DISP_ALWAYS, GCStrings::getString(127).c_str());
		}
		// pratipat system
		if (GCTithi::TITHI_TRANSIT(s.astrodata.nTithi, t.astrodata.nTithi, TITHI_PURNIMA, TITHI_KRSNA_PRATIPAT))
		{
			if (GCDisplaySettings::getValue(CATURMASYA_PRATIPAT))
			{
				tmp.Format("%s [PRATIPAT SYSTEM]", GCStrings::getString(124).c_str());
				t.AddEvent(PRIO_CM_DAY, CATURMASYA_PRATIPAT, tmp.c_str());
				t.AddEvent(PRIO_CM_DAYNOTE, CATURMASYA_PRATIPAT, GCStrings::getString(126).c_str());
				t.nCaturmasya = CMASYA_SYSTEM_PRATIPAT | CMASYA_DAY_FIRST | CMASYA_MONTH_4;
				tmp.Format("%s [PRATIPAT SYSTEM]", GCStrings::getString(121).c_str());
				s.AddEvent(PRIO_CM_DAY, CATURMASYA_PRATIPAT, tmp.c_str());
				s.nCaturmasya = CMASYA_SYSTEM_PRATIPAT | CMASYA_DAY_LAST | CMASYA_MONTH_3;
			}
		}

		// last day for punima system
		if (GCTithi::TITHI_TRANSIT(t.astrodata.nTithi, u.astrodata.nTithi, TITHI_GAURA_CATURDASI, TITHI_PURNIMA))
		{
			if (GCDisplaySettings::getValue(CATURMASYA_PURNIMA))
			{
				tmp.Format("%s [PURNIMA SYSTEM]", GCStrings::getString(125).c_str());
				t.AddEvent(PRIO_CM_DAY, CATURMASYA_PURNIMA, tmp.c_str());
				t.nCaturmasya = CMASYA_SYSTEM_PURNIMA | CMASYA_DAY_LAST | CMASYA_MONTH_4;
			}
		}

		// ekadasi system
		//if (TITHI_TRANSIT(t.astrodata.nTithi, u.astrodata.nTithi, TITHI_GAURA_DASAMI, TITHI_GAURA_EKADASI))
		if ((t.astrodata.nPaksa == GAURA_PAKSA) && (t.nMhdType != EV_NULL))
		{
			if (GCDisplaySettings::getValue(CATURMASYA_EKADASI))
			{
				tmp.Format("%s [EKADASI SYSTEM]", GCStrings::getString(125).c_str());
				s.AddEvent(PRIO_CM_DAY, CATURMASYA_EKADASI, tmp.c_str());
				s.nCaturmasya = CMASYA_SYSTEM_EKADASI | CMASYA_DAY_LAST | CMASYA_MONTH_4;
			}
		}

		if (GCTithi::TITHI_TRANSIT(t.astrodata.nTithi, u.astrodata.nTithi, TITHI_PURNIMA, TITHI_KRSNA_PRATIPAT))
		{
			if (GCDisplaySettings::getValue(CATURMASYA_PRATIPAT))
			{
				tmp.Format("%s [PRATIPAT SYSTEM]", GCStrings::getString(125).c_str());
				t.AddEvent(PRIO_CM_DAY, CATURMASYA_PRATIPAT, tmp.c_str());
				t.nCaturmasya = CMASYA_SYSTEM_PRATIPAT | CMASYA_DAY_LAST | CMASYA_MONTH_4;
			}

			// on last day of Caturmasya pratipat system is Bhisma Pancaka ending
			//if (t.festivals.GetLength() > 0)
			//	t.festivals += "#";
			//t.festivals += GCStrings::getString(82);
			t.AddEvent(PRIO_CM_DAYNOTE, DISP_ALWAYS, GCStrings::getString(82).c_str());
		}
	}

	return 1;
}


/******************************************************************************************/
/*                                                                                        */
/*                                                                                        */
/*                                                                                        */
/******************************************************************************************/

int TResultCalendar::MahadvadasiCalc(int nIndex, EARTHDATA earth)
{
	int nMahaType = NULL;
	int nMhdDay = -1;

	VAISNAVADAY & s = m_pData[nIndex-1];
	VAISNAVADAY & t = m_pData[nIndex];
	VAISNAVADAY & u = m_pData[nIndex+1];

	// if yesterday is dvadasi
	// then we skip this day
	if (GCTithi::TITHI_DVADASI(s.astrodata.nTithi))
		return 1;

	if (TITHI_GAURA_DVADASI == t.astrodata.nTithi && TITHI_GAURA_DVADASI == t.astrodata.nTithiSunset && IsMhd58(nIndex, nMahaType))
	{
		t.nMhdType = nMahaType;
		nMhdDay = nIndex;
	}
	else if (GCTithi::TITHI_DVADASI(t.astrodata.nTithi))
	{
		if (GCTithi::TITHI_DVADASI(u.astrodata.nTithi) && GCTithi::TITHI_EKADASI(s.astrodata.nTithi) && GCTithi::TITHI_EKADASI(s.astrodata.nTithiArunodaya))
		{
			t.nMhdType = EV_VYANJULI;
			nMhdDay = nIndex;
		}
		else if (NextNewFullIsVriddhi(nIndex, earth))
		{
			t.nMhdType = EV_PAKSAVARDHINI;
			nMhdDay = nIndex;
		}
		else if (GCTithi::TITHI_LESS_EKADASI(s.astrodata.nTithiArunodaya))
		{
			t.nMhdType = EV_SUDDHA;
			nMhdDay = nIndex;
		}
	}

	if (nMhdDay >= 0)
	{
		// fasting day
		m_pData[nMhdDay].nFastType = FAST_EKADASI;
		m_pData[nMhdDay].ekadasi_vrata_name = GCStrings::GetEkadasiName(t.astrodata.nMasa, t.astrodata.nPaksa);
		m_pData[nMhdDay].ekadasi_parana = false;
		m_pData[nMhdDay].eparana_time1 = 0.0;
		m_pData[nMhdDay].eparana_time2 = 0.0;

		// parana day
		m_pData[nMhdDay + 1].nFastType = FAST_NULL;
		m_pData[nMhdDay + 1].ekadasi_parana = true;
		m_pData[nMhdDay + 1].eparana_time1 = 0.0;
		m_pData[nMhdDay + 1].eparana_time2 = 0.0;
	}

	return 1;
}

VAISNAVADAY * TResultCalendar::GetDay(int nIndex)
{
	int nReturn = nIndex + BEFORE_DAYS;

	if (nReturn >= m_nCount)
		return NULL;

	return &m_pData[nReturn];
}

/******************************************************************************************/
/*                                                                                        */
/*                                                                                        */
/*                                                                                        */
/******************************************************************************************/

int TResultCalendar::ExtendedCalc(int nIndex, EARTHDATA earth)
{
	VAISNAVADAY & s = m_pData[nIndex-1];
	VAISNAVADAY & t = m_pData[nIndex];
	VAISNAVADAY & u = m_pData[nIndex+1];
	VAISNAVADAY & v = m_pData[nIndex+2];

	// test for Rama Navami
	if ((t.astrodata.nMasa == VISNU_MASA) && (t.astrodata.nPaksa == GAURA_PAKSA))
	{
		if (IsFestivalDay(s, t, TITHI_GAURA_NAVAMI))
		{
			if (u.nFastType >= FAST_EKADASI)
			{
				// yesterday was Rama Navami
				s.AddSpecFestival(SPEC_RAMANAVAMI, CAL_FEST_0);
				//s.nFastType = FAST_SUNSET;
			}
			else
			{
				// today is Rama Navami
				t.AddSpecFestival(SPEC_RAMANAVAMI, CAL_FEST_0);
				//t.nFastType = FAST_SUNSET;
			}
		}
	}

	return 1;
}

/******************************************************************************************/
/*                                                                                        */
/*  TEST if today is given festival tithi                                                 */
/*                                                                                        */
/*  if today is given tithi and yesterday is not this tithi                               */
/*  then it is festival day (it is first day of this tithi, when vriddhi)                 */
/*                                                                                        */
/*  if yesterday is previous tithi to the given one and today is next to the given one    */
/*  then today is day after ksaya tithi which is given                                    */
/*                                                                                        */
/*                                                                                        */
/******************************************************************************************/

bool TResultCalendar::IsFestivalDay(VAISNAVADAY &yesterday, VAISNAVADAY &today, int nTithi)
{
	return ((today.astrodata.nTithi == nTithi) && GCTithi::TITHI_LESS_THAN(yesterday.astrodata.nTithi, nTithi))
			|| (GCTithi::TITHI_LESS_THAN(yesterday.astrodata.nTithi, nTithi) && GCTithi::TITHI_GREAT_THAN(today.astrodata.nTithi, nTithi));

}

int TResultCalendar::FindDate(VCTIME vc)
{
	int i;
	for(i = BEFORE_DAYS; i < m_nCount; i++)
	{
		if ((m_pData[i].date.day == vc.day) && (m_pData[i].date.month == vc.month) && (m_pData[i].date.year == vc.year))
			return (i - BEFORE_DAYS);
	}

	return -1;
}

int TResultCalendar::CalculateEParana(VAISNAVADAY &s, VAISNAVADAY &t, double &begin, double &end, EARTHDATA earth)
{
	t.nMhdType = EV_NULL;
	t.ekadasi_parana = true;
	t.nFastType = FAST_NULL;

	double titBeg, titEnd, tithi_quart;
	double sunRise, third_day, naksEnd;
	double parBeg = -1.0, parEnd = -1.0;
	double tithi_len;
	VCTIME snd, nend;

	sunRise = t.astrodata.sun.sunrise_deg / 360.0 + earth.tzone / 24.0;
	third_day = sunRise + t.astrodata.sun.length_deg / 1080.0;
	tithi_len = GCTithi::GetTithiTimes(earth, t.date, titBeg, titEnd, sunRise);
	tithi_quart = tithi_len / 4.0 + titBeg;

	switch(s.nMhdType)
	{
	case EV_UNMILANI:
		parEnd = titEnd;
		t.eparana_type2 = EP_TYPE_TEND;
		if (parEnd > third_day)
		{
			parEnd = third_day;
			t.eparana_type2 = EP_TYPE_3DAY;
		}
		parBeg = sunRise;
		t.eparana_type1 = EP_TYPE_SUNRISE;
		break;
	case EV_VYANJULI:
		parBeg = sunRise;
		t.eparana_type1 = EP_TYPE_SUNRISE;
		parEnd = GCMath::Min(titEnd, third_day);
		if (parEnd == titEnd)
			t.eparana_type2 = EP_TYPE_TEND;
		else
			t.eparana_type2 = EP_TYPE_3DAY;
		break;
	case EV_TRISPRSA:
		parBeg = sunRise;
		parEnd = third_day;
		t.eparana_type1 = EP_TYPE_SUNRISE;
		t.eparana_type2 = EP_TYPE_3DAY;
		break;
	case EV_JAYANTI:
	case EV_VIJAYA:

		naksEnd = GCNaksatra::GetEndHour(earth, s.date, t.date); //GetNextNaksatra(earth, snd, nend);
		if (GCTithi::TITHI_DVADASI(t.astrodata.nTithi))
		{
			if (naksEnd < titEnd)
			{
				if (naksEnd < third_day)
				{
					parBeg = naksEnd;
					t.eparana_type1 = EP_TYPE_NAKEND;
					parEnd = GCMath::Min(titEnd, third_day);
					if (parEnd == titEnd)
						t.eparana_type2 = EP_TYPE_TEND;
					else
						t.eparana_type2 = EP_TYPE_3DAY;
				}
				else
				{
					parBeg = naksEnd;
					t.eparana_type1 = EP_TYPE_NAKEND;
					parEnd = titEnd;
					t.eparana_type2 = EP_TYPE_TEND;
				}
			}
			else
			{
				parBeg = sunRise;
				t.eparana_type1 = EP_TYPE_SUNRISE;
				parEnd = GCMath::Min(titEnd, third_day);
				if (parEnd == titEnd)
					t.eparana_type2 = EP_TYPE_TEND;
				else
					t.eparana_type2 = EP_TYPE_3DAY;
			}
		}
		else
		{
			parBeg = sunRise;
			t.eparana_type1 = EP_TYPE_SUNRISE;
			parEnd = GCMath::Min( naksEnd, third_day );
			if (parEnd == naksEnd)
				t.eparana_type2 = EP_TYPE_NAKEND;
			else
				t.eparana_type2 = EP_TYPE_3DAY;
		}

		break;
	case EV_JAYA:
	case EV_PAPA_NASINI:

		naksEnd = GCNaksatra::GetEndHour(earth, s.date, t.date); //GetNextNaksatra(earth, snd, nend);

		if (GCTithi::TITHI_DVADASI(t.astrodata.nTithi))
		{
			if (naksEnd < titEnd)
			{
				if (naksEnd < third_day)
				{
					parBeg = naksEnd;
					t.eparana_type1 = EP_TYPE_NAKEND;
					parEnd = GCMath::Min(titEnd, third_day);
					if (parEnd == titEnd)
						t.eparana_type2 = EP_TYPE_TEND;
					else
						t.eparana_type2 = EP_TYPE_3DAY;
				}
				else
				{
					parBeg = naksEnd;
					t.eparana_type1 = EP_TYPE_NAKEND;
					parEnd = titEnd;
					t.eparana_type2 = EP_TYPE_TEND;
				}
			}
			else
			{
				parBeg = sunRise;
				t.eparana_type1 = EP_TYPE_SUNRISE;
				parEnd = GCMath::Min(titEnd, third_day);
				if (parEnd == titEnd)
					t.eparana_type2 = EP_TYPE_TEND;
				else
					t.eparana_type2 = EP_TYPE_3DAY;
			}
		}
		else
		{
			if (naksEnd < third_day)
			{
				parBeg = naksEnd;
				t.eparana_type1 = EP_TYPE_NAKEND;
				parEnd = third_day;
				t.eparana_type2 = EP_TYPE_3DAY;
			}
			else
			{
				parBeg = naksEnd;
				t.eparana_type1 = EP_TYPE_NAKEND;
				parEnd = -1.0;
				t.eparana_type2 = EP_TYPE_NULL;
			}
		}

		break;
	default:
		// first initial
		parEnd = GCMath::Min(titEnd, third_day);
		if (parEnd == titEnd)
			t.eparana_type2 = EP_TYPE_TEND;
		else
			t.eparana_type2 = EP_TYPE_3DAY;
		parBeg = GCMath::Max(sunRise, tithi_quart);
		if (parBeg == sunRise)
			t.eparana_type1 = EP_TYPE_SUNRISE;
		else
			t.eparana_type1 = EP_TYPE_4TITHI;

		if (GCTithi::TITHI_DVADASI(s.astrodata.nTithi))
		{
			parBeg = sunRise;
			t.eparana_type1 = EP_TYPE_SUNRISE;
		}

		//if (parBeg > third_day)
		if (parBeg > parEnd)
		{	
//			parBeg = sunRise;
			parEnd = -1.0;
			t.eparana_type2 = EP_TYPE_NULL;
		}
		break;
	}


	begin = parBeg;
	end = parEnd;

	if (begin > 0.0)
		begin *= 24.0;
	if (end > 0.0)
		end *= 24.0;

	t.eparana_time1 = begin;
	t.eparana_time2 = end;

	return 1;
}


/* Function before is writen accoring this algorithms:


1. Normal - fasting day has ekadasi at sunrise and dvadasi at next sunrise.

2. Viddha - fasting day has dvadasi at sunrise and trayodasi at next
sunrise, and it is not a naksatra mahadvadasi

3. Unmilani - fasting day has ekadasi at both sunrises

4. Vyanjuli - fasting day has dvadasi at both sunrises, and it is not a
naksatra mahadvadasi

5. Trisprsa - fasting day has ekadasi at sunrise and trayodasi at next
sunrise.

6. Jayanti/Vijaya - fasting day has gaura dvadasi and specified naksatra at
sunrise and same naksatra at next sunrise

7. Jaya/Papanasini - fasting day has gaura dvadasi and specified naksatra at
sunrise and same naksatra at next sunrise

==============================================
Case 1 Normal (no change)

If dvadasi tithi ends before 1/3 of daylight
   then PARANA END = TIME OF END OF TITHI
but if dvadasi TITHI ends after 1/3 of daylight
   then PARANA END = TIME OF 1/3 OF DAYLIGHT

if 1/4 of dvadasi tithi is before sunrise
   then PARANA BEGIN is sunrise time
but if 1/4 of dvadasi tithi is after sunrise
   then PARANA BEGIN is time of 1/4 of dvadasi tithi

if PARANA BEGIN is before PARANA END
   then we will write "BREAK FAST FROM xx TO yy
but if PARANA BEGIN is after PARANA END
   then we will write "BREAK FAST AFTER xx"

==============================================
Case 2 Viddha

If trayodasi tithi ends before 1/3 of daylight
   then PARANA END = TIME OF END OF TITHI
but if trayodasi TITHI ends after 1/3 of daylight
   then PARANA END = TIME OF 1/3 OF DAYLIGHT

PARANA BEGIN is sunrise time

we will write "BREAK FAST FROM xx TO yy

==============================================
Case 3 Unmilani

PARANA END = TIME OF 1/3 OF DAYLIGHT

PARANA BEGIN is end of Ekadasi tithi

if PARANA BEGIN is before PARANA END
   then we will write "BREAK FAST FROM xx TO yy
but if PARANA BEGIN is after PARANA END
   then we will write "BREAK FAST AFTER xx"

==============================================
Case 4 Vyanjuli

PARANA BEGIN = Sunrise

PARANA END is end of Dvadasi tithi

we will write "BREAK FAST FROM xx TO yy

==============================================
Case 5 Trisprsa

PARANA BEGIN = Sunrise

PARANA END = 1/3 of daylight hours

we will write "BREAK FAST FROM xx TO yy

==============================================
Case 6 Jayanti/Vijaya

PARANA BEGIN = Sunrise

PARANA END1 = end of dvadasi tithi or sunrise, whichever is later
PARANA END2 = end of naksatra

PARANA END is earlier of END1 and END2

we will write "BREAK FAST FROM xx TO yy

==============================================
Case 7 Jaya/Papanasini

PARANA BEGIN = end of naksatra

PARANA END = 1/3 of Daylight hours

if PARANA BEGIN is before PARANA END
   then we will write "BREAK FAST FROM xx TO yy
but if PARANA BEGIN is after PARANA END
   then we will write "BREAK FAST AFTER xx"



  */



void TResultCalendar::ResolveFestivalsFasting(int nIndex)
{
	VAISNAVADAY & s = m_pData[nIndex-1];
	VAISNAVADAY & t = m_pData[nIndex];
	VAISNAVADAY & u = m_pData[nIndex+1];

	int nftype;
	TString pers, str, S;
	TString subject;
	int fasting = 0;
	const char * ch;
	GCMutableDictionary * md;

	if (t.nMhdType != EV_NULL)
	{
		str.Format("%s %s", GCStrings::getString(87).c_str(), t.ekadasi_vrata_name);
		t.AddEvent(PRIO_EKADASI, CAL_EKADASI_PARANA, str.c_str());
	}

	ch = GCStrings::GetMahadvadasiName(t.nMhdType);
	if (ch != NULL)
	{
		t.AddEvent(PRIO_MAHADVADASI, CAL_EKADASI_PARANA, ch);
	}

	if (t.ekadasi_parana)
	{
		t.GetTextEP(str);
		t.AddEvent(PRIO_EKADASI_PARANA, CAL_EKADASI_PARANA, str.c_str());
	}

	for(int h = 0; h < t.dayEvents.Count(); h++)
	{
		md = t.dayEvents.ObjectAtIndex(h);
		nftype = 0;
		if (md->containsKey("fasttype"))
		{
			nftype = md->intForKey("fasttype");
			subject = md->stringForKey("fastsubject");
		}

		if (nftype != 0)
		{
			if (s.nFastType == FAST_EKADASI)
			{
				if (GCDisplaySettings::getValue(42)==0)
				{
					str.Format("(Fast today for %s)", subject.c_str());
					s.AddEvent(PRIO_FASTING, DISP_ALWAYS, str.c_str());
					t.AddEvent(md->intForKey("prio") + 1, md->intForKey("disp"), GCStrings::getString(860).c_str());
					//"(Fasting is done yesterday)"
				}
				else
				{
					str.Format("(Fast till noon for %s, with feast tomorrow)", subject.c_str());
					s.AddEvent(PRIO_FASTING, DISP_ALWAYS, str.c_str());
					t.AddEvent(md->intForKey("prio") + 1, md->intForKey("disp"), GCStrings::getString(861).c_str());
					//"(Fasting is done yesterday, today is feast)";
				}
			}
			else if (t.nFastType == FAST_EKADASI)
			{
				if (GCDisplaySettings::getValue(42)!=0)
					t.AddEvent(md->intForKey("prio") + 1, md->intForKey("disp"), GCStrings::getString(862).c_str());
					//"(Fasting till noon, with feast tomorrow)";
				else
					t.AddEvent(md->intForKey("prio") + 1, md->intForKey("disp"), GCStrings::getString(756).c_str());
					//"(Fast today)"
			}
			else
			{
				if (GCDisplaySettings::getValue(42) == 0)
				{
					if (nftype>1)
						nftype=7;
					else nftype = 0;
				}
				if (nftype != 0)
				{
					t.AddEvent(md->intForKey("prio") + 1, md->intForKey("disp"), 
						GCStrings::GetFastingName(0x200 + nftype));
				}
			}
		}
		if (fasting < nftype)
			fasting = nftype;
	}

	if (fasting)
	{
		if (s.nFastType == FAST_EKADASI)
		{
			t.nFeasting = FEAST_TODAY_FAST_YESTERDAY;
			s.nFeasting = FEAST_TOMMOROW_FAST_TODAY;
		}
		else if (t.nFastType == FAST_EKADASI)
		{
			u.nFeasting = FEAST_TODAY_FAST_YESTERDAY;
			t.nFeasting = FEAST_TOMMOROW_FAST_TODAY;
		}
		else
		{
			t.nFastType = (FastType)(0x200 + fasting);
		}
	}

}

int TResultCalendar::writeXml(FILE * fout)
{
	int k;
	TString str, st;
	VCTIME date;
	TFileXml xml;
	VAISNAVADAY * pvd;
	int nPrevMasa = -1;
	int nPrevPaksa = -1;
	TResultCalendar &daybuff = *this;
	xml.initWithFile(fout);

	xml.write("<xml>\n");
	xml.write("\t<request name=\"Calendar\" version=\"");
	xml.write(GCStrings::getString(130));
	xml.write("\">\n");
	xml.write("\t\t<arg name=\"longitude\" val=\"");
	xml.write(daybuff.m_Location.m_fLongitude);
	xml.write("\" />\n");
	xml.write("\t\t<arg name=\"latitude\" val=\"");
	xml.write(daybuff.m_Location.m_fLatitude);
	xml.write("\" />\n");
	xml.write("\t\t<arg name=\"timezone\" val=\"");
	xml.write(daybuff.m_Location.m_fTimezone);
	xml.write("\" />\n");
	xml.write("\t\t<arg name=\"startdate\" val=\"");
	xml.write(daybuff.m_vcStart);
	xml.write("\" />\n");
	xml.write("\t\t<arg name=\"daycount\" val=\"");
	xml.write(daybuff.m_vcCount);
	xml.write("\" />\n");
	xml.write("\t\t<arg name=\"dst\" val=\"");
	xml.write(daybuff.m_Location.m_nDST);
	xml.write("\" />\n");
	xml.write("\t</request>\n");
	xml.write("\t<result name=\"Calendar\">\n");
	if (daybuff.m_Location.m_nDST > 0)
		xml.write("\t<dstsystem name=\"");
	xml.write(TTimeZone::GetTimeZoneName(daybuff.m_Location.m_nDST));
	xml.write("\" />\n");

	for (k = 0; k < daybuff.m_vcCount; k++)
	{
		pvd = daybuff.GetDay(k);
		if (pvd)
		{
			if (nPrevMasa != pvd->astrodata.nMasa)
			{
				if (nPrevMasa != -1)
					xml.write("\t</masa>\n");
				xml.write("\t<masa name=\"");
				xml.write(GCStrings::GetMasaName(pvd->astrodata.nMasa));
				xml.write(" Masa");
				if (nPrevMasa == ADHIKA_MASA)
					xml.write(" ");
				xml.write(GCStrings::getString(109));
				xml.write("\"");
				xml.write(" gyear=\"Gaurabda ");
				xml.write(pvd->astrodata.nGaurabdaYear);
				xml.write("\"");
				xml.write(">\n");
			}

			nPrevMasa = pvd->astrodata.nMasa;

			// date data
			xml.write("\t<day date=\"");
			xml.write(pvd->date);
			xml.write("\" dayweekid=\"");
			xml.write(pvd->date.dayOfWeek);
			xml.write("\" dayweek=\"");
			GCStrings::getString(pvd->date.dayOfWeek).Left(2, st);
			xml.write(st.c_str());
			xml.write("\">\n");

			// sunrise data
			xml.write("\t\t<sunrise time=\"");
			xml.write(pvd->astrodata.sun.rise);
			xml.write("\">\n");

			xml.write("\t\t\t<tithi name=\"");
			xml.write(pvd->GetFullTithiName());
			str.Format("\" elapse=\"%.1f\" index=\"%d\"/>\n"
				,pvd->astrodata.nTithiElapse, pvd->astrodata.nTithi % 30 + 1 );
			xml.write(str);

			str.Format("\t\t\t<naksatra name=\"%s\" elapse=\"%.1f\" />\n"
				,GCStrings::GetNaksatraName(pvd->astrodata.nNaksatra), pvd->astrodata.nNaksatraElapse );
			xml.write(str);

			str.Format("\t\t\t<yoga name=\"%s\" />\n", GCStrings::GetYogaName(pvd->astrodata.nYoga) );
			xml.write(str);

			str.Format("\t\t\t<paksa id=\"%c\" name=\"%s\"/>\n", GCStrings::GetPaksaChar(pvd->astrodata.nPaksa), GCStrings::GetPaksaName(pvd->astrodata.nPaksa) );
			xml.write(str);

			xml.write("\t\t</sunrise>\n");

			xml.write("\t\t<dst offset=\"");
			xml.write(pvd->nDST);
			xml.write("\" />\n");
			// arunodaya data
			xml.write("\t\t<arunodaya time=\"");
			xml.write(pvd->astrodata.sun.arunodaya);
			xml.write("\">\n");
			xml.write("\t\t\t<tithi name=\"");
			xml.write(GCStrings::GetTithiName(pvd->astrodata.nTithiArunodaya));
			xml.write("\" />\n");
			xml.write("\t\t</arunodaya>\n");

			str.Empty();

			xml.write("\t\t<noon time=\"");
			xml.write(pvd->astrodata.sun.noon);
			xml.write("\" />\n");

			xml.write("\t\t<sunset time=\"");
			xml.write(pvd->astrodata.sun.set);
			xml.write("\" />\n");

			// moon data
			xml.write("\t\t<moon rise=\"");
			xml.write(pvd->moonrise);
			xml.write("\" set=\"");
			xml.write(pvd->moonset);
			xml.write("\" />\n");

			if (pvd->ekadasi_parana)
			{
				double h1, m1, h2, m2;
				m1 = modf(pvd->eparana_time1, &h1);
				if (pvd->eparana_time2 >= 0.0)
				{
					m2 = modf(pvd->eparana_time2, &h2);
					str.Format("\t\t<parana from=\"%02d:%02d\" to=\"%02d:%02d\" />\n", int(h1), int(m1*60), int(h2), int(m2*60));
				}
				else
				{
					str.Format("\t\t<parana after=\"%02d:%02d\" />\n", int(h1), int(m1*60) );
				}
				xml.write(str);
			}
			str.Empty();

			for(int h = 0; h < pvd->dayEvents.Count(); h++)
			{
				GCMutableDictionary * md = pvd->dayEvents.ObjectAtIndex(h);
				int prio = md->intForKey("prio");
				if (prio >= PRIO_FESTIVALS_0 && prio <= PRIO_FESTIVALS_6)
				{
					str.Format("\t\t<festival name=\"%s\" class=\"%d\"/>\n", md->stringForKey("text"), md->intForKey("disp") - CAL_FEST_0);
					xml.write(str);
				}
			}

			if (pvd->nFastType != FAST_NULL)
			{
				xml.write("\t\t<fast type=\"\" mark=\"");
				if (pvd->nFastType == FAST_EKADASI)
				{
					xml.write("*");
				}
				xml.write("\" />\n");
			}

			if (pvd->sankranti_zodiac >= 0)
			{
				//double h1, m1, s1;
				//m1 = modf(pvd->sankranti_day.shour*24, &h1);
				//				s1 = modf(m1*60, &m1);
				str.Format("\t\t<sankranti rasi=\"%s\" time=\"%02d:%02d:%02d\" />\n"
					, GCStrings::GetSankrantiName(pvd->sankranti_zodiac), pvd->sankranti_day.GetHour()
					, pvd->sankranti_day.GetMinute(), pvd->sankranti_day.GetSecond());
				xml.write(str);
			}

			if (pvd->nCaturmasya != 0)
			{
				xml.write("\t\t<caturmasya day=\"" );
				xml.write((pvd->nCaturmasya & CMASYA_DAY_MASK) == CMASYA_DAY_FIRST ? "last" : "first");
				xml.write("\" month=\"");
				xml.write((int)(pvd->nCaturmasya & CMASYA_MONTH_MASK) - CMASYA_MONTH_MASK + 1);
				xml.write("\" system=\"");
				if ((pvd->nCaturmasya & CMASYA_SYSTEM_MASK) == CMASYA_SYSTEM_PURNIMA)
					xml.write("PURNIMA");
				else if ((pvd->nCaturmasya & CMASYA_SYSTEM_MASK) == CMASYA_SYSTEM_PRATIPAT)
					xml.write("PRATIPAT");
				else
					xml.write("EKADASI");
				xml.write("\" />\n");
			}

			xml.write("\t</day>\n\n");

		}
		date.shour = 0;
		date.NextDay();
	}
	xml.write("\t</masa>\n");


	xml.write("</result>\n");
	xml.write("</xml>\n");

	return 1;
}

int TResultCalendar::formatPlainText(TString &m_text) 
{
	int k, nMasaHeader;
	TString str, str2, str3, dayText;

	char * spaces = "                                                                                ";
	VAISNAVADAY * pvd, * prevd, *nextd;
	int lastmasa = -1;
	int lastmonth = -1;
	int tp1;
	double rate;
	bool bCalcMoon = (GCDisplaySettings::getValue(4) > 0 || GCDisplaySettings::getValue(5) > 0);
	TResultCalendar &daybuff = * this;
	m_text.Empty();
	GCStringBuilder sb;

	sb.Target = &m_text;
	sb.Format = SBTF_TEXT;

	for (k = 0; k < daybuff.m_vcCount; k++)
	{

		prevd = daybuff.GetDay(k - 1);
		pvd = daybuff.GetDay(k);
		nextd = daybuff.GetDay(k + 1);

		if (updateCalculationProgress)
		{
			if (bCalcMoon)
			{
				GCUserInterface::SetProgressWindowPos(int(90.8 + 9.2 * k / daybuff.m_vcCount));
			}
			else
			{
				rate = double(k) / daybuff.m_vcCount;
				GCUserInterface::SetProgressWindowPos(int(58.8 + 41.2 * rate * rate));
			}
		}

		if (pvd)
		{
			nMasaHeader = 0;
			if ((GCDisplaySettings::getValue(18) == 1) && (pvd->astrodata.nMasa != lastmasa))
			{
				nMasaHeader = 1;
				m_text += "\r\n";
				str.Format("%s %s, Gaurabda %d", GCStrings::GetMasaName(pvd->astrodata.nMasa), GCStrings::getString(22).c_str(), pvd->astrodata.nGaurabdaYear);
				tp1 = (80 - str.GetLength())/2;
				str.Insert(0, (spaces + (79 - tp1)));
				str += spaces;
				str.Insert(80 - _tcslen(GCStrings::GetVersionText()), GCStrings::GetVersionText());
				str.Left(80, str3);
				m_text += str3;
				m_text += "\r\n";
				if ((pvd->astrodata.nMasa == ADHIKA_MASA) && ((lastmasa >= SRIDHARA_MASA) && (lastmasa <= DAMODARA_MASA)))
				{
					sb.AppendTwoColumnText("", GCStrings::getString(128));
				}
				m_text += "\r\n";
				lastmasa = pvd->astrodata.nMasa;
			}

			if ((GCDisplaySettings::getValue(19) == 1) && (pvd->date.month != lastmonth))
			{
				nMasaHeader = 1;
				m_text += "\r\n";
				str.Format("%s %d", GCStrings::getString(759 + pvd->date.month).c_str(), pvd->date.year);
				tp1 = (80 - str.GetLength())/2;
				str.Insert(0, (spaces + (79 - tp1)));
				str += spaces;
				str.Insert(80 - _tcslen(GCStrings::GetVersionText()), GCStrings::GetVersionText());
				str.Left(80, str3);
				m_text += str3;
				m_text += "\r\n";
				lastmonth = pvd->date.month;
			}
			if (nMasaHeader)
			{
				m_text += (spaces + (79 - (80 - daybuff.m_Location.m_strFullName.GetLength())/2));
				m_text += daybuff.m_Location.m_strFullName;

				m_text += "\r\n\r\n";
				/* BEGIN GCAL 1.4.3 */
				nMasaHeader = m_text.GetLength();
				m_text += " DATE            TITHI                         ";
				if (GCDisplaySettings::getValue(39)) m_text += "PAKSA ";
				else m_text += "      ";
				if (GCDisplaySettings::getValue(37)) m_text += "YOGA      ";
				if (GCDisplaySettings::getValue(36)) m_text += "NAKSATRA       ";
				if (GCDisplaySettings::getValue(38)) m_text += "FAST ";
				if (GCDisplaySettings::getValue(41)) m_text += "RASI           ";
				nMasaHeader = m_text.GetLength() - nMasaHeader;
				m_text += "\r\n";
				/* END GCAL 1.4.3 */
				while(nMasaHeader > 0)
				{
					m_text += "----------";
					nMasaHeader -= 10;
				}
				nMasaHeader = 0;
				m_text += "\r\n";
			}

			TResultCalendar::formatPlainTextDay(pvd, dayText);

			if (GCDisplaySettings::getValue(20) == 0)
				m_text += dayText;
			else if (pvd->dayEvents.Count() > 0)
				m_text += dayText;


		}
//		date.shour = 0;
//		date.NextDay();
	}

	sb.AppendNote();
//	dcp.DestroyWindow();

	return 1;
}

int TResultCalendar::formatPlainTextDay(VAISNAVADAY * pvd, TString &dayText) 
{
	TString str, str2, str3;

	GCStringBuilder sb;
	sb.Format = SBTF_TEXT;
	sb.Target = &dayText;

	dayText.Empty();
	pvd->GetTextA(str, GCDisplaySettings::getValue(39), GCDisplaySettings::getValue(36), GCDisplaySettings::getValue(37), GCDisplaySettings::getValue(38), GCDisplaySettings::getValue(41));
	str.Mid(16, str.GetLength(), str2);
	str.Left(15, str3);
	str = str3;
	if (pvd->astrodata.sun.longitude_deg < 0.0)
	{
		sb.AppendTwoColumnText(str, "No rise and no set of the sun. No calendar information.");
		return 1;
	}
	sb.AppendTwoColumnText(str, str2);


	for(int i = 0; i < pvd->dayEvents.Count(); i++)
	{
		GCMutableDictionary * ed = pvd->dayEvents.ObjectAtIndex(i);
		int disp = ed->intForKey("disp");
		if (!ed->containsKey("disp") || disp == -1 || GCDisplaySettings::getValue(disp))
		{
			if (ed->containsKey("spec"))
			{
				str = ed->stringForKey("text");
				int length = str.GetLength();
				length = (82 - length) / 2;
				sb.AppendSeparatorWithWidth(length);
				sb.AppendString(str.c_str());
				sb.AppendSeparatorWithWidth(length);
				sb.AppendLine();
			}
			else
			{
				sb.AppendTwoColumnText("", ed->stringForKey("text"));
			}
		}
	}

	return 0;
}

/****************************************************************************
*
*
*
*****************************************************************************/

int TResultCalendar::formatRtf(TString &m_text) 
{
	int k;
	int bShowColumnHeaders = 0;
	TString str, str2, str3, dayText;

	char * spaces = "                                                                                ";
	VAISNAVADAY * pvd, * prevd, *nextd;
	int lastmasa = -1;
	int lastmonth = -1;
//	int tp1;
//	double rate;
	bool bCalcMoon = (GCDisplaySettings::getValue(4) > 0 || GCDisplaySettings::getValue(5) > 0);
	TResultCalendar &daybuff = *this;
	m_text.Empty();

	GCStringBuilder sb;

	sb.Target = &m_text;
	sb.Format = SBTF_RTF;
	sb.fontSizeH1 = GCLayoutData::textSizeH1;
	sb.fontSizeH2 = GCLayoutData::textSizeH2;
	sb.fontSizeText = GCLayoutData::textSizeText;
	sb.fontSizeNote = GCLayoutData::textSizeNote;

	sb.AppendDocumentHeader();

	for (k = 0; k < daybuff.m_vcCount; k++)
	{

		prevd = daybuff.GetDay(k - 1);
		pvd = daybuff.GetDay(k);
		nextd = daybuff.GetDay(k + 1);

		if (pvd)
		{
			bShowColumnHeaders = 0;
			if ((GCDisplaySettings::getValue(18) == 1) && (pvd->astrodata.nMasa != lastmasa))
			{
				if (bShowColumnHeaders == 0)
					m_text += "\\par ";
				bShowColumnHeaders = 1;
//				m_text += "\\par\r\n";
				str.Format("\\par \\pard\\f2\\fs%d\\qc %s %s, Gaurabda %d", GCLayoutData::textSizeH2
					, GCStrings::GetMasaName(pvd->astrodata.nMasa), GCStrings::getString(22).c_str()
					, pvd->astrodata.nGaurabdaYear);
				if ((pvd->astrodata.nMasa == ADHIKA_MASA) && ((lastmasa >= SRIDHARA_MASA) && (lastmasa <= DAMODARA_MASA)))
				{
					str += "\\line ";
					str += GCStrings::getString(128).c_str();
				}
				m_text += str;

				lastmasa = pvd->astrodata.nMasa;
			}

			if ((GCDisplaySettings::getValue(19) == 1) && (pvd->date.month != lastmonth))
			{
				if (bShowColumnHeaders == 0)
					m_text += "\\par ";
				bShowColumnHeaders = 1;
				str.Format("\\par\\pard\\f2\\qc\\fs%d\r\n", GCLayoutData::textSizeH2);
				m_text += str;
				str.Format("%s %d", GCStrings::getString(759 + pvd->date.month).c_str(), pvd->date.year);
				m_text += str;
				lastmonth = pvd->date.month;
			}

			// print location text
			if (bShowColumnHeaders)
			{
				m_text += "\\par\\pard\\qc\\cf2\\fs22 ";
				m_text += daybuff.m_Location.m_strFullName;
			}

			if (bShowColumnHeaders)
			{
				str.Format("\\par\\pard\\fs%d\\qc %s", GCLayoutData::textSizeNote, GCStrings::GetVersionText());
				m_text += str;
				m_text += "\\par\\par\r\n";
			}


			if (bShowColumnHeaders)
			{
				int tabStop = 5760*GCLayoutData::textSizeText/24;
				str.Format("\\pard\\tx%d\\tx%d ",2000*GCLayoutData::textSizeText/24, tabStop);
				m_text += str;
				if (GCDisplaySettings::getValue(39))
				{
					tabStop += 990*GCLayoutData::textSizeText/24;
					str.Format("\\tx%d", tabStop);
					m_text += str;
				}
				if (GCDisplaySettings::getValue(37))
				{
					tabStop += 1720*GCLayoutData::textSizeText/24;
					str.Format("\\tx%d", tabStop);
					m_text += str;
				}
				if (GCDisplaySettings::getValue(36))
				{
					tabStop += 1800*GCLayoutData::textSizeText/24;
					str.Format("\\tx%d", tabStop);
					m_text += str;
				}
				if (GCDisplaySettings::getValue(38))
				{
					tabStop += 750*GCLayoutData::textSizeText/24;
					str.Format("\\tx%d", tabStop);
					m_text += str;
				}
				if (GCDisplaySettings::getValue(41))
				{
					tabStop += 1850*GCLayoutData::textSizeText/24;
					str.Format("\\tx%d", tabStop);
					m_text += str;
				}
				// paksa width 990
				// yoga width 1720
				// naks width 1800
				// fast width 990
				// rasi width 1850
				m_text += str;
				str.Format("{\\highlight15\\cf7\\fs%d\\b DATE\\tab TITHI", GCLayoutData::textSizeNote);
				m_text += str;
				if (GCDisplaySettings::getValue(39)) {
					m_text += "\\tab PAKSA";
				}
				if (GCDisplaySettings::getValue(37)) {
					m_text += "\\tab YOGA";
				}
				if (GCDisplaySettings::getValue(36)) {
					m_text += "\\tab NAKSATRA";
				}
				if (GCDisplaySettings::getValue(38))
				{
					m_text += "\\tab FAST";
				}
				if (GCDisplaySettings::getValue(41))
				{
					m_text += "\\tab RASI";
				}
				m_text += "}";
				//m_text += "\\par\r\n";
				//m_text += gpszSeparator;
			}
			str.Format("\\fs%d ", GCLayoutData::textSizeText);
			m_text += str;
//			m_text += "\\fs24 ";

			TResultCalendar::formatRtfDay(pvd, dayText);

			if (GCDisplaySettings::getValue(20) == 0)
				m_text += dayText;
			else if (pvd->dayEvents.Count())
				m_text += dayText;


		}
	}

	sb.AppendNote();
	sb.AppendDocumentTail();

	return 1;
}

int TResultCalendar::formatRtfDay(VAISNAVADAY * pvd, TString &dayText) 
{
	TString str, str2, str3;

	dayText.Empty();

	GCStringBuilder sb;
	
	sb.Target = &dayText;
	sb.Format = SBTF_RTF;
	sb.fontSizeH1 = GCLayoutData::textSizeH1;
	sb.fontSizeH2 = GCLayoutData::textSizeH2;
	sb.fontSizeText = GCLayoutData::textSizeText;
	sb.fontSizeNote = GCLayoutData::textSizeNote;

	if (pvd->astrodata.sun.longitude_deg < 0.0)
	{
		dayText += "\\par\\tab No rise and no set of the sun. No calendar information.";
		return 1;
	}

	pvd->GetTextRtf(str, GCDisplaySettings::getValue(39), GCDisplaySettings::getValue(36), GCDisplaySettings::getValue(37), GCDisplaySettings::getValue(38), GCDisplaySettings::getValue(41));
	dayText += str;

	for(int i = 0; i < pvd->dayEvents.Count(); i++)
	{
		GCMutableDictionary * ed = pvd->dayEvents.ObjectAtIndex(i);
		int disp = ed->intForKey("disp");
		if (!ed->containsKey("disp") || disp == -1 || GCDisplaySettings::getValue(disp))
		{
			if (ed->containsKey("spec"))
			{
				str = ed->stringForKey("text");
				int length = str.GetLength();
				length = (80 - length) / 2;
				dayText += "\\par ";
				sb.AppendSeparatorWithWidth(length);
				dayText += str;
				sb.AppendSeparatorWithWidth(length);
			}
			else
			{
				sb.AppendTwoColumnText("", ed->stringForKey("text"));
			}
		}
	}

	return 0;
}


int TResultCalendar::formatICal(TString &m_text)
{
	int k;
	int initialLength = 0;
	int lastmasa = -1;
	int lastmonth = -1;
	int tzoffset = 0, tzoff;
	TString str, dayText, str2, str3;
	VAISNAVADAY * pvd, * prevd, * nextd;
	char * SPACE_BEFORE_LINE = " , ";
	VCTIME vc;
	TResultCalendar &daybuff = *this;
	CLocationRef &loc = daybuff.m_Location;

	SYSTEMTIME st;
	GetLocalTime(&st);

	m_text = "BEGIN:VCALENDAR\nVERSION:2.0\nX-WR-CALNAME:VAISNAVA\nPRODID:-//GBC Calendar Comitee//GCAL//EN\n";
	m_text += "X-WR-RELCALID:";
	str2.Format("%08d-%04d-%04d-%04d-%04d%08d", st.wYear + st.wMilliseconds, st.wDay * st.wDayOfWeek, st.wMonth,
		st.wHour, st.wMinute + st.wMilliseconds);
	m_text += str2;
	m_text += "\nX-WR-TIMEZONE:";

	m_text += TTimeZone::GetTimeZoneName(loc.m_nDST);
	m_text += "\n";

	m_text += "CALSCALE:GREGORIAN\nMETHOD:PUBLISH\n";
	m_text += "BEGIN:VTIMEZONE\nTZID:";
	m_text += TTimeZone::GetTimeZoneName(loc.m_nDST);
	str2.Format("\nLAST-MODIFIED:%04d%02d%02dT%02d%02d%02dZ", st.wYear, st.wMonth, st.wDay, st.wHour, st.wMinute, st.wSecond);
	m_text += str2;

	tzoffset = int(TTimeZone::GetTimeZoneOffset(loc.m_nDST) * 60.0);
	tzoff = tzoffset + TTimeZone::GetTimeZoneBias(loc.m_nDST);

	if (TTimeZone::GetTimeZoneBias(loc.m_nDST) > 0)
	{
		TTimeZone::GetDaylightTimeStartDate(loc.m_nDST, st.wYear, vc);
		str2.Format("\nBEGIN:DAYLIGHT\nDTSTART:%04d%02d%02dT%02d0000", vc.year, vc.month, vc.day, vc.GetHour());
		m_text += str2;

		str2.Format("\nTZOFFSETTO:%c%02d%02d", (tzoff > 0 ? '+' : '-'), abs(tzoff) / 60, abs(tzoff) % 60);
		m_text += str2;

		str2.Format("\nTZOFFSETFROM:%c%02d%02d", '+', 0, 0);
		m_text += str2;

		TTimeZone::GetNormalTimeStartDate(loc.m_nDST, st.wYear, vc);
		m_text += "\nEND:DAYLIGHT\nBEGIN:STANDARD\nDTSTART:";
		str2.Format("%04d%02d%02dT%02d0000", vc.year, vc.month, vc.day, vc.GetHour());
		m_text += str2;

		str2.Format("\nTZOFFSETTO:%c%02d%02d", (tzoffset > 0 ? '+' : '-'), abs(tzoffset)/60, abs(tzoffset) % 60);
		m_text += str2;
		str2.Format("\nTZOFFSETFROM:%c%02d%02d", (tzoff > 0 ? '+' : '-'), abs(tzoff) / 60, abs(tzoff) % 60);
		m_text += str2;
		m_text += "\nEND:STANDARD\n";
	}
	else
	{
		m_text += "\nBEGIN:STANDARD\nDTSTART:";
		str2.Format("%04d0101T000000", vc.year, vc.month, vc.day, vc.GetHour());
		m_text += str2;

		str.Format("\nTZOFFSETTO:%+02d%02d", tzoffset/60, abs(tzoffset) % 60);
		m_text += str2;
		str2.Format("\nTZOFFSETFROM:+0000");
		m_text += str2;
		m_text += "\nEND:STANDARD\n";
	}

	m_text += "END:VTIMEZONE\n";

	for (k = 0; k < daybuff.m_PureCount; k++)
	{
//		date.shour = 0.0;
//		date.TimeZone = earth.tzone;

		prevd = daybuff.GetDay(k - 1);
		pvd = daybuff.GetDay(k);
		nextd = daybuff.GetDay(k + 1);

		if (pvd)
		{
			dayText.Empty();

			if (pvd->astrodata.nMasa != lastmasa)
			{
				str.Format("%s %s, Gaurabda %d", GCStrings::GetMasaName(pvd->astrodata.nMasa), GCStrings::getString(22).c_str(), pvd->astrodata.nGaurabdaYear);
				dayText += str;
				dayText += "\n";
				if ((pvd->astrodata.nMasa == ADHIKA_MASA) && ((lastmasa >= SRIDHARA_MASA) && (lastmasa <= DAMODARA_MASA)))
				{
					if (dayText.IsEmpty() == FALSE)
						dayText += SPACE_BEFORE_LINE;
					dayText += GCStrings::getString(128);
					dayText += "\n";
				}

				lastmasa = pvd->astrodata.nMasa;
				initialLength = -1;
			}
			else
			{
				initialLength = 0;
			}

			if (dayText.IsEmpty() == FALSE)
				dayText += SPACE_BEFORE_LINE;
			dayText += pvd->GetFullTithiName();
			dayText += "\n";

			initialLength += dayText.GetLength();

			if (pvd->astrodata.sun.longitude_deg < 0.0)
			{
				goto _resolve_text;
			}

			for(int i = 0; i < pvd->dayEvents.Count(); i++)
			{
				GCMutableDictionary * ed = pvd->dayEvents.ObjectAtIndex(i);
				int disp = ed->intForKey("disp");
				if (!ed->containsKey("disp") || disp == -1 || GCDisplaySettings::getValue(disp))
				{
					dayText += SPACE_BEFORE_LINE;
					dayText += ed->stringForKey("text");
					dayText += "\n";
				}
			}

		_resolve_text:
			if (dayText.GetLength() > initialLength)
			{
				m_text += "BEGIN:VEVENT\n";
				str2.Format("DTSTART;VALUE=DATE:%04d%02d%02d\n", pvd->date.year, pvd->date.month, pvd->date.day);
				m_text += str2;
				/*str2.Format("DTEND;VALUE=DATE:%04d%02d%02dT%02d%02d%02d\n", pvd->date.year, pvd->date.month, pvd->date.day,
					pvd->astrodata.sun.set.hour, pvd->astrodata.sun.set.min, pvd->astrodata.sun.set.sec);
				m_text += str2;*/
				str2.Format("LOCATION:%s\n", loc.m_strName.c_str());
				str2.Replace(",", "\\,");
				m_text += str2;
				m_text += "SUMMARY:";
				dayText.TrimLeft();
				dayText.Replace(",", "\\,");
				m_text += dayText;
				str2.Format("UID:%08d-%04d-%04d-%04d-%08d%04d\n", st.wYear, st.wMonth*30 + st.wDay, st.wHour*60 + st.wMinute, st.wSecond, st.wMilliseconds, k);
				m_text += str2;
				m_text += "DURATION:P1D\nSEQUENCE:1\nEND:VEVENT\n";
			}
		}
	}

	m_text += "END:VCALENDAR\n";
	return 1;
}



int TResultCalendar::formatVCal(TString &m_text)
{
	int k;
	int initialLength = 0;
	int lastmasa = -1;
	int lastmonth = -1;
	int tzoffset = 0;
	TString str, dayText, str2, str3;
	VAISNAVADAY * pvd, * prevd, * nextd;
	char * SPACE_BEFORE_LINE = " , ";
	VCTIME vc;
	TResultCalendar &daybuff = *this;
	SYSTEMTIME st;
	GetLocalTime(&st);

	m_text = "BEGIN:VCALENDAR\nVERSION:1.0\nX-WR-CALNAME:VAISNAVA\nPRODID:-//GBC Calendar Comitee//GCAL//EN\n";
	m_text += "X-WR-RELCALID:";
	str2.Format("%08d-%04d-%04d-%04d-%04d%08d", st.wYear + st.wMilliseconds, st.wDay * st.wDayOfWeek, st.wMonth,
		st.wHour, st.wMinute + st.wMilliseconds);
	m_text += str2;
	m_text += "\nX-WR-TIMEZONE:";

	m_text += TTimeZone::GetTimeZoneName(daybuff.m_Location.m_nDST);
	m_text += "\n";

	m_text += "CALSCALE:GREGORIAN\nMETHOD:PUBLISH\n";
	/*m_text += "BEGIN:VTIMEZONE\nTZID:";
	m_text += TTimeZone::GetTimeZoneName(nDst);
	str2.Format("\nLAST-MODIFIED:%04d%02d%02dT%02d%02d%02dZ", st.wYear, st.wMonth, st.wDay, st.wHour, st.wMinute, st.wSecond);
	m_text += str2;

	tzoffset = int(TTimeZone::GetTimeZoneOffset(nDst) * 60.0);
	tzoff = tzoffset + TTimeZone::GetTimeZoneBias(nDst);

	if (TTimeZone::GetTimeZoneBias(nDst) > 0)
	{
		TTimeZone::GetDaylightTimeStartDate(nDst, st.wYear, vc);
		str2.Format("\nBEGIN:DAYLIGHT\nDTSTART:%04d%02d%02dT%02d0000", vc.year, vc.month, vc.day, vc.GetHour());
		m_text += str2;

		str2.Format("\nTZOFFSETTO:%c%02d%02d", (tzoff > 0 ? '+' : '-'), abs(tzoff) / 60, abs(tzoff) % 60);
		m_text += str2;

		str2.Format("\nTZOFFSETFROM:%c%02d%02d", '+', 0, 0);
		m_text += str2;

		TTimeZone::GetNormalTimeStartDate(nDst, st.wYear, vc);
		m_text += "\nEND:DAYLIGHT\nBEGIN:STANDARD\nDTSTART:";
		str2.Format("%04d%02d%02dT%02d0000", vc.year, vc.month, vc.day, vc.GetHour());
		m_text += str2;

		str2.Format("\nTZOFFSETTO:%c%02d%02d", (tzoffset > 0 ? '+' : '-'), abs(tzoffset)/60, abs(tzoffset) % 60);
		m_text += str2;
		str2.Format("\nTZOFFSETFROM:%c%02d%02d", (tzoff > 0 ? '+' : '-'), abs(tzoff) / 60, abs(tzoff) % 60);
		m_text += str2;
		m_text += "\nEND:STANDARD\n";
	}
	else
	{
		m_text += "\nBEGIN:STANDARD\nDTSTART:";
		str2.Format("%04d0101T000000", vc.year, vc.month, vc.day, vc.GetHour());
		m_text += str2;

		str.Format("\nTZOFFSETTO:%+02d%02d", tzoffset/60, abs(tzoffset) % 60);
		m_text += str2;
		str2.Format("\nTZOFFSETFROM:+0000");
		m_text += str2;
		m_text += "\nEND:STANDARD\n";
	}

	m_text += "END:VTIMEZONE\n";
*/
	for (k = 0; k < daybuff.m_PureCount; k++)
	{
//		date.shour = 0.0;
//		date.TimeZone = earth.tzone;

		prevd = daybuff.GetDay(k - 1);
		pvd = daybuff.GetDay(k);
		nextd = daybuff.GetDay(k + 1);

		if (pvd)
		{
			dayText.Empty();

			if (pvd->astrodata.nMasa != lastmasa)
			{
				str.Format("%s %s, Gaurabda %d", GCStrings::GetMasaName(pvd->astrodata.nMasa), GCStrings::getString(22).c_str(), pvd->astrodata.nGaurabdaYear);
				dayText += str;
				dayText += "\n";
				if ((pvd->astrodata.nMasa == ADHIKA_MASA) && ((lastmasa >= SRIDHARA_MASA) && (lastmasa <= DAMODARA_MASA)))
				{
					if (dayText.IsEmpty() == FALSE)
						dayText += SPACE_BEFORE_LINE;
					dayText += GCStrings::getString(128);
					dayText += "\n";
				}

				lastmasa = pvd->astrodata.nMasa;
				initialLength = -1;
			}
			else
			{
				initialLength = 0;
			}

			if (dayText.IsEmpty() == FALSE)
				dayText += SPACE_BEFORE_LINE;
			dayText += pvd->GetFullTithiName();
			dayText += "\n";

			initialLength += dayText.GetLength();

			if (pvd->astrodata.sun.longitude_deg < 0.0)
			{
				goto _resolve_text;
			}

//			if (GCDisplaySettings::getValue(17) == 1)
			{
				double h1, m1;
				if (pvd->ekadasi_parana)
				{
					m_text += "BEGIN:VEVENT\n";
					if (pvd->eparana_time1 >= 0.0) {
						m1 = modf(pvd->eparana_time1, &h1) * 60;
						str2.Format("DTSTART:%04d%02d%02dT%02d%02d00\n", pvd->date.year, pvd->date.month, pvd->date.day, int(h1), int(m1));
					} else {
						str2.Format("DTSTART:%04d%02d%02dT000000\n", pvd->date.year, pvd->date.month, pvd->date.day);
					}
					m_text += str2;
					if (pvd->eparana_time2 >= 0.0) {
						m1 = modf(pvd->eparana_time2, &h1) * 60;
						str2.Format("DTEND:%04d%02d%02dT%02d%02d00\n", pvd->date.year, pvd->date.month, pvd->date.day, int(h1), int(m1));
					} else {
						str2.Format("DTEND:%04d%02d%02dT%02d%02d%00\n", pvd->date.year, pvd->date.month, pvd->date.day, pvd->astrodata.sun.set.hour, pvd->astrodata.sun.set.min);
					}
					m_text += str2;
					m_text += "SUMMARY:";
					m_text += GCStrings::getString(60);
					m_text += "\nSEQUENCE:1\nEND:VEVENT\n";

				}
			}

			for(int i = 0; i < pvd->dayEvents.Count(); i++)
			{
				GCMutableDictionary * ed = pvd->dayEvents.ObjectAtIndex(i);
				int disp = ed->intForKey("disp");
				if (!ed->containsKey("disp") || disp == -1 || GCDisplaySettings::getValue(disp))
				{
					dayText += SPACE_BEFORE_LINE;
					dayText += ed->stringForKey("text");
					dayText += "\n";
				}
			}

		_resolve_text:
			if (dayText.GetLength() > initialLength)
			{
				m_text += "BEGIN:VEVENT\n";
				str2.Format("DTSTART:%04d%02d%02dT%02d%02d%02d\n", pvd->date.year, pvd->date.month, pvd->date.day,
					pvd->astrodata.sun.rise.hour, pvd->astrodata.sun.rise.min, pvd->astrodata.sun.rise.sec);
				m_text += str2;
				str2.Format("DTEND:%04d%02d%02dT%02d%02d%02d\n", pvd->date.year, pvd->date.month, pvd->date.day,
					pvd->astrodata.sun.set.hour, pvd->astrodata.sun.set.min, pvd->astrodata.sun.set.sec);
				m_text += str2;
				str2.Format("LOCATION:%s\n", daybuff.m_Location.m_strName.c_str());
				str2.Replace(",", "\\,");
				m_text += str2;
				m_text += "SUMMARY:";
				dayText.TrimLeft();
				dayText.Replace(",", "\\,");
				m_text += dayText;
				str2.Format("UID:%08d-%04d-%04d-%04d-%08d%04d\n", st.wYear, st.wMonth*30 + st.wDay, st.wHour*60 + st.wMinute, st.wSecond, st.wMilliseconds, k);
				m_text += str2;
				m_text += "SEQUENCE:1\nEND:VEVENT\n";
			}
		}
	}

	m_text += "END:VCALENDAR\n";
	return 1;
}


int TResultCalendar::formatCsv(TString &m_text)
{
	int k;
	int initialLength = 0;
	int lastmasa = -1;
	int lastmonth = -1;
	int tzoffset = 0;
	TString str, dayText, str2, str3;
	VAISNAVADAY * pvd, * prevd, * nextd;
	char * SPACE_BEFORE_LINE = " , ";
	VCTIME vc;

	TResultCalendar &daybuff = *this;
	SYSTEMTIME st;
	GetLocalTime(&st);

	m_text = "\"Subject\",\"Begin Date\",\"Start\",\"End Date\",\"End\",\"WholeDay\",\"Alarm\"\n";

	for (k = 0; k < daybuff.m_PureCount; k++)
	{
		prevd = daybuff.GetDay(k - 1);
		pvd = daybuff.GetDay(k);
		nextd = daybuff.GetDay(k + 1);

		if (pvd)
		{
			dayText.Empty();

			if (pvd->astrodata.nMasa != lastmasa)
			{
				lastmasa = pvd->astrodata.nMasa;
				initialLength = -1;
			}
			else
			{
				initialLength = 0;
			}

			if (dayText.IsEmpty() == FALSE)
				dayText += SPACE_BEFORE_LINE;
			dayText += pvd->GetFullTithiName();
			dayText += "; ";

			initialLength = dayText.GetLength();

			if (pvd->astrodata.sun.longitude_deg < 0.0)
			{
				goto _resolve_text;
			}

			for(int i = 0; i < pvd->dayEvents.Count(); i++)
			{
				GCMutableDictionary * ed = pvd->dayEvents.ObjectAtIndex(i);
				int disp = ed->intForKey("disp");
				if (!ed->containsKey("disp") || disp == -1 || GCDisplaySettings::getValue(disp))
				{
					dayText += ed->stringForKey("text");
					dayText += "; ";
				}
			}


  			//"Sunrise Time",//2
			//"Sunset Time",//3


		_resolve_text:
			if (dayText.GetLength() > initialLength || (GCDisplaySettings::getValue(20) == 0))
			{
				m_text += "\"";
				m_text += dayText;
				m_text += "\",";

				str2.Format("\"%d.%d.%d\",\"0:00:00\",\"%d.%d.%d\",\"0:00:00\",\"True\",\"False\"\n", 
					pvd->date.day, pvd->date.month, pvd->date.year, nextd->date.day,
					nextd->date.month, nextd->date.year);
				m_text += str2;
			}
		}
	}

	return 1;
}

const char * TResultCalendar::getDayBkgColorCode(VAISNAVADAY * p)
{
	if (p == NULL)
		return "white";
	if (p->nFastType == FAST_EKADASI)
		return "#FFFFBB";
	if (p->nFastType != 0)
		return "#BBFFBB";
	return "white";
}

/******************************************************************************************/
/*                                                                                        */
/*                                                                                        */
/*                                                                                        */
/*                                                                                        */
/*                                                                                        */
/*                                                                                        */
/*                                                                                        */
/*                                                                                        */
/*                                                                                        */
/*                                                                                        */
/*                                                                                        */
/*                                                                                        */
/*                                                                                        */
/*                                                                                        */
/*                                                                                        */
/******************************************************************************************/

int TResultCalendar::writeHtml(FILE * fout)
{
	int k;
	TString str, st;
	TString daystr;
	VCTIME date;
	TFileXml xml;
	VAISNAVADAY * pvd;
	int nPrevMasa = -1;
	int nPrevPaksa = -1;
	TResultCalendar &daybuff = *this;

	xml.initWithFile(fout);

	xml.write("<html><head><title>\n");
	xml.write("Calendar ");
	xml.write(daybuff.m_vcStart.year);
	xml.write("</title>");
	xml.write("<style>\n");
	xml.write("<!--\nbody {\n");
	xml.write("  font-family:Verdana;\n");
	xml.write("  font-size:11pt;\n}\n\n");
	xml.write("td.hed {\n");
	xml.write("  font-family:Verdana;\n");
	xml.write("  font-size:9pt;\n");
	xml.write("  font-weight:bold;\n");
	xml.write("  background:#aaaaaa;\n");
	xml.write("  color:white;\n");
	xml.write("  text-align:center;\n");
	xml.write("  vertical-align:center;\n  padding-left:15pt;\n  padding-right:15pt;\n");
	xml.write("  padding-top:5pt;\n  padding-bottom:5pt;\n}\n-->\n</style>\n");
	xml.write("</head>\n<body>");

	for (k = 0; k < daybuff.m_vcCount; k++)
	{
		pvd = daybuff.GetDay(k);
		if (pvd)
		{
			if (nPrevMasa != pvd->astrodata.nMasa)
			{
				if (nPrevMasa != -1)
					xml.write("\t</table>\n");
				xml.write("<p style=\'text-align:center;font-weight:bold\'><span style =\'font-size:14pt\'>");
				xml.write(GCStrings::GetMasaName(pvd->astrodata.nMasa));
				xml.write(" Masa");
				if (nPrevMasa == ADHIKA_MASA)
					xml.write(" ");
				xml.write(GCStrings::getString(109));
				xml.write("</span>");
				xml.write("<br><span style=\'font-size:10pt;\'>Gaurabda ");
				xml.write(pvd->astrodata.nGaurabdaYear);
				xml.write("<br>");
				xml.write(daybuff.m_Location.m_strFullName);
				xml.write("</font>");
				xml.write("</span></p>\n<table align=center>");
				xml.write("<tr><td  class=\"hed\"colspan=2>");
				xml.write("DATE</td><td class=\"hed\">TITHI</td><td class=\"hed\">P</td><td class=\"hed\">NAKSATRA</td><td class=\"hed\">YOGA</td><td class=\"hed\">FAST</td></tr>");
			}

			nPrevMasa = pvd->astrodata.nMasa;

			if (pvd->dayEvents.Count() > 0)
				 continue;

			// date data
			xml.write("<tr>");
			xml.write("<td>");
			xml.write(pvd->date);
			xml.write("</td><td>");
			GCStrings::getString(pvd->date.dayOfWeek).Left(2, st);
			xml.write(st.c_str());
			xml.write("</td>\n");

			// sunrise data

			xml.write("<td>\n");
			xml.write(pvd->GetFullTithiName());
			xml.write("</td>\n");

			str.Format("<td>%c</td>\n", GCStrings::GetPaksaChar(pvd->astrodata.nPaksa) );
			xml.write(str);

			str.Format("<td>%s</td>\n", GCStrings::GetNaksatraName(pvd->astrodata.nNaksatra));
			xml.write(str);

			str.Format("<td>%s</td>\n", GCStrings::GetYogaName(pvd->astrodata.nYoga) );
			xml.write(str);


			xml.write("<td>");
			xml.write(((pvd->nFastType!=FAST_NULL)?"FAST</td>":"</td>"));

			str.Empty();


			xml.write("</tr>\n\n<tr>\n<td></td><td></td><td colspan=4>");
			for(int i = 0; i < pvd->dayEvents.Count(); i++)
			{
				GCMutableDictionary * ed = pvd->dayEvents.ObjectAtIndex(i);
				int disp = ed->intForKey("disp");
				if (!ed->containsKey("disp") || disp == -1 || GCDisplaySettings::getValue(disp))
				{
					if (ed->containsKey("spec"))
					{
						xml.write("<font color=\"blue\">");
						xml.write(ed->stringForKey("text"));
						xml.write("</font><br>\n");
					}
					else
					{
						xml.write(ed->stringForKey("text"));
						xml.write("<br>\n");
					}
				}
			}
	
			xml.write("\t</tr>\n\n");

		}
		date.shour = 0;
		date.NextDay();
	}
	xml.write("\t</table>\n\n");

	xml.write("<hr align=center width=\"65%%\">\n");
	xml.write("<p align=center>Generated by ");
	xml.write(GCStrings::getString(130));
	xml.write("</p>\n");
	
	xml.write("</body>\n</html>\n");

	return 1;
}


/******************************************************************************************/
/*                                                                                        */
/*                                                                                        */
/*                                                                                        */
/*                                                                                        */
/*                                                                                        */
/*                                                                                        */
/*                                                                                        */
/*                                                                                        */
/*                                                                                        */
/*                                                                                        */
/*                                                                                        */
/*                                                                                        */
/*                                                                                        */
/*                                                                                        */
/*                                                                                        */
/******************************************************************************************/

int TResultCalendar::writeTableHtml(FILE * fout)
{
	int g_firstday_in_week = GCDisplaySettings::getValue(GENERAL_FIRST_DOW);
	int k, y, lwd;
	TString str, st;
	VCTIME date;
	TFileXml xml;
	VAISNAVADAY * pvd;
	int nPrevMasa = -1;
	int prevMas = -1;
	int brw = 0;

	TResultCalendar &daybuff = *this;

	// first = 1
	//int i_end[7] = {0, 6, 5, 4, 3, 2, 1}; //(6-(i-first))%7
	//int i_beg[7] = {6, 0, 1, 2, 3, 4, 5}; //(i-first)%7
	xml.initWithFile(fout);

	xml.write("<html>\n<head>\n<title>Calendar ");
	xml.write(daybuff.m_vcStart);
	xml.write("</title>\n");

	xml.write("<style>\n<!--\np.MsoNormal, li.MsoNormal, div.MsoNormal\n	{mso-style-parent:\"\";");
	xml.write("margin:0in;margin-bottom:.0001pt;mso-pagination:widow-orphan;font-size:8.0pt;font-family:Arial;");
	xml.write("mso-fareast-font-family:Arial;}");
	xml.write("p.month\n{mso-style-name:month;\nmso-margin-top-alt:auto;\nmargin-right:0in;\nmso-margin-bottom-alt:auto;\nmargin-left:0in;\nmso-pagination:widow-orphan;\nfont-size:17.0pt;font-family:Arial;mso-fareast-font-family:Arial;}\n");
	xml.write(".text\n{mso-style-name:text;\nmso-margin-top-alt:auto;\nmargin-right:0in;\nmso-margin-bottom-alt:auto;\nmargin-left:0in;\n	mso-pagination:widow-orphan;\nfont-size:6.0pt;\nmso-bidi-font-size:6.0pt;\nfont-family:Arial;	mso-fareast-font-family:\"Arial\";mso-bidi-font-family:\"Arial\";}\n");
	xml.write(".tnote\n{mso-style-name:text;\nmso-margin-top-alt:auto;\nmargin-right:0in;\nmso-margin-bottom-alt:auto;\nmargin-left:0in;\n	mso-pagination:widow-orphan;\nfont-size:7.0pt;\nmso-bidi-font-size:7.0pt;\nfont-family:Arial;	mso-fareast-font-family:Arial;mso-bidi-font-family:Arial;}\n");
	xml.write(".tithiname\n{mso-style-name:text;\nmso-margin-top-alt:auto;\nmargin-right:0in;\nmso-margin-bottom-alt:auto;\nmargin-left:0in;\n	mso-pagination:widow-orphan;\nfont-size:8.0pt;\nmso-bidi-font-size:8.0pt;\nfont-family:Arial;	mso-fareast-font-family:\"Arial\";mso-bidi-font-family:\"Arial\";}\n");
	xml.write(".dayt\n	{mso-style-name:dayt;\nfont-size:12.0pt;\nmso-ansi-font-size:12.0pt;\nfont-family:Arial;\nmso-ascii-font-family:Arial;\nmso-hansi-font-family:Arial;\nfont-weight:bold;\nmso-bidi-font-weight:normal;}\n");
	xml.write("span.SpellE\n{mso-style-name:\"\";\nmso-spl-e:yes;}\n");
	xml.write("span.GramE\n{mso-style-name:\"\";\nmso-gram-e:yes;}\n");
	xml.write("-->\n</style>\n");

	xml.write("</head>\n\n<body>\n\n");

	for (k = 0; k < daybuff.m_vcCount; k++)
	{
		pvd = daybuff.GetDay(k);
		if (pvd)
		{
			BOOL bSemicolon = false;
			BOOL bBr = false;
			lwd = pvd->date.dayOfWeek;
			if (nPrevMasa != pvd->date.month)
			{
				int y;
				if (nPrevMasa != -1)
				{
					for(y = 0; y < DAYS_TO_ENDWEEK(lwd); y++)
					{
						xml.write("<td style=\'border:solid windowtext 1.0pt;mso-border-alt:solid windowtext .5pt;padding:3.0pt 3.0pt 3.0pt 3.0pt\'>&nbsp;</td>");
					}
					xml.write("</tr></table>\n<p>&nbsp;</p>");
				}
				xml.write("\n<table width=\"100%\" border=0 frame=bottom cellspacing=0 cellpadding=0><tr><td width=\"60%\"><p class=month>");
				xml.write(GCStrings::getString(pvd->date.month + 759).c_str());
				xml.write(" ");
				xml.write(pvd->date.year);
				xml.write("</p></td><td><p class=tnote align=right>");
				xml.write(daybuff.m_Location.m_strName);
				xml.write("<br>Timezone: ");
				xml.write(TTimeZone::GetTimeZoneName(daybuff.m_Location.m_nDST));
				xml.write("</p>");
				xml.write("</td></tr></table><hr>");
				nPrevMasa = pvd->date.month;
				xml.write("\n<table width=\"100%\" bordercolor=black cellpadding=0 cellspacing=0>\n<tr>\n");
				for(y = 0; y < 7; y++)
				{
					xml.write("<td width=\"14%\" align=center style=\'font-size:10.0pt;border:none\'>");
					xml.write(GCStrings::getString(DAY_INDEX(y)).c_str());
					xml.write("</td>\n");
				}
				xml.write("<tr>\n");
				for(y=0; y < DAYS_FROM_BEGINWEEK(pvd->date.dayOfWeek); y++)
					xml.write("<td style=\'border:solid windowtext 1.0pt;mso-border-alt:solid windowtext .5pt;padding:3.0pt 3.0pt 3.0pt 3.0pt\'>&nbsp;</td>");
			}
			else
			{
				if (pvd->date.dayOfWeek == g_firstday_in_week)
					xml.write("<tr>\n");
			}

			// date data
			xml.write("\n<td valign=top style=\'border:solid windowtext 1.0pt;mso-border-alt:solid windowtext .5pt;padding:3.0pt 3.0pt 3.0pt 3.0pt\' ");
			xml.write("bgcolor=\"");
			xml.write(TResultCalendar::getDayBkgColorCode(pvd));
			xml.write("\">");
			xml.write("<table width=\"100%\" border=0><tr><td><p class=text><span class=dayt>");
			xml.write(pvd->date.day);
			xml.write("</span></td><td>");


			xml.write("<span class=\"tithiname\">");
			xml.write(pvd->GetFullTithiName());
			xml.write("</span>");
			xml.write("</td></tr></table>\n");
			brw = 0;
			xml.write("<span class=\"text\">\n");

			str.Empty();

			if (pvd->dayEvents.Count() > 0)
			{
				if (brw)
					xml.write("<br>\n");
				brw = 1;
				bSemicolon = false;
			}

			for(int i = 0; i < pvd->dayEvents.Count(); i++)
			{
				GCMutableDictionary * ed = pvd->dayEvents.ObjectAtIndex(i);
				int disp = ed->intForKey("disp");
				if (!ed->containsKey("disp") || disp == -1 || GCDisplaySettings::getValue(disp))
				{
					if (bSemicolon)
						xml.write("; ");
					bSemicolon=true;
					if (ed->containsKey("spec"))
					{
						xml.write(ed->stringForKey("text"));
					}
					else
					{
						str.Format("<i>%s</i>\n", ed->stringForKey("text"));
						xml.write(str);
					}
				}
			}


			if (prevMas != pvd->astrodata.nMasa)
			{
				if (brw)
					xml.write("<br>\n");
				brw = 1;
				xml.write("<b>[");
				xml.write(GCStrings::GetMasaName(pvd->astrodata.nMasa));
				xml.write(" Masa]</b>");
				prevMas = pvd->astrodata.nMasa;
			}
			xml.write("</span>");
			xml.write("</td>\n\n");

		}
		date.shour = 0;
		date.NextDay();
	}

	for(y = 1; y < DAYS_TO_ENDWEEK(lwd); y++)
	{
		xml.write("<td style=\'border:solid windowtext 1.0pt;mso-border-alt:solid windowtext .5pt;padding:3.0pt 3.0pt 3.0pt 3.0pt\'>&nbsp;</td>");
	}

	xml.write("</tr>\n</table>\n");
	xml.write("</body>\n</html>\n");

	return 1;
}
