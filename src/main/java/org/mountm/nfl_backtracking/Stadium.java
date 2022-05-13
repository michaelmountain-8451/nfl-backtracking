package org.mountm.nfl_backtracking;

public enum Stadium {

	BAL(0, 0), BOS(1, 0), NYY(2, 0), TBR(3), TOR(4, 0),
	CWS(5), CLE(6), DET(7, 0), KCR(8), MIN(9, 1),
	HOU(10), LAA(11, 3), OAK(12, 3), SEA(13, 3), TEX(14, 1),
	ATL(15, 0), MIA(16, 0), NYM(17, 0), PHI(18, 0), WAS(19),
	CHC(20, 1), CIN(21, 0), MIL(22, 1), PIT(23, 0), STL(24, 1),
	ARI(25), COL(26, 2), LAD(27), SDP(28, 3), SFG(29, 3),
	CLB(30), IND(31, 0), IOW(32), LOU(33), OMA(34), STP(35, 1), TOL(36, 0),
	BUF(37, 0), LEH(38), ROC(39, 0), SWB(40, 0), SYR(41), WOR(42),
	CHR(43, 0), DUR(44), GWI(45, 0), JAX(46, 0), MEM(47), NSH(48, 1), NFK(49, 0),
	ABQ(50), EPC(51), OKC(52), RRE(53), SUG(54),
	LVA(55, 3), REN(56, 3), SAC(57, 3), SLB(58), TAC(59, 3),
	ARK(60, 1), NWA(61, 1), SPR(62), TUL(63, 1), WWS(64),
	AMA(65), CCH(66), FRR(67), MID(68), SAM(69),
	BNG(70), HAR(71), NHF(72), POR(73, 0), RED(74), SOM(75, 0),
	AKR(76, 0), ALT(77, 0), BOW(78), ERI(79, 0), HBG(80), RCH(81),
	BMG(82), CHT(83), RTP(84), TEN(85, 0),
	BLX(86), MSB(87, 1), MON(88, 1), PEN(89, 1),
	ABR(90), BRK(91), HUD(92), JER(93, 0), WLM(94),
	ASH(95), BGH(96), GRN(97), GRV(98), HCK(99, 0), ROM(100, 0), WSD(101),
	DAY(102), FTW(103, 0), GLL(104, 0), LCC(105, 0), LAN(106), WMI(107),
	BLT(108), CED(109, 1), PEO(110), QCR(111, 1), SBC(112, 0), WIS(113, 1),
	EUG(114), EVT(115), HIL(116), SPK(117), TRI(118), VAN(119),
	DMV(120, 0), FRD(121), LYN(122), SLM(123, 0),
	CAR(124), DEW(125), FAY(126), KAN(127),
	AUG(128, 0), CHL(129), CMB(130, 0), MYR(131),
	DYT(132), RDS(133), SLU(134),
	BRD(135), CLR(136, 0), DUN(137), FTM(138, 0), LKL(139), TPA(140, 0),
	FRS(141, 3), MOD(142), SJG(143, 3), STK(144),
	INL(145, 3), LES(146), RCQ(147), VIS(148);

	private final int index;

	private final int timeZone;

	private static int[][] minutesBetween;

	Stadium(int index) {
		this.index = index;
	}

	Stadium(int index, int timeZone) {
		this.index = index;
		this.timeZone = timeZone;
	}

	public int getIndex() {
		return index;
	}

	public int getTimeZone() {
		return timeZone;
	}

	public static void setMinutesBetweenData(int[][] minutesBetweenData) {
		minutesBetween = minutesBetweenData;
	}

	public int getMinutesTo(Stadium stadium) {
		return minutesBetween[index][stadium.index];
	}
}
