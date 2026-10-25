       IDENTIFICATION DIVISION.
       PROGRAM-ID. IDXDENSE.
       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT DFILE ASSIGN TO "dense.dat"
               ORGANIZATION IS INDEXED
               ACCESS MODE IS DYNAMIC
               RECORD KEY IS D-KEY
               ALTERNATE RECORD KEY IS D-GRP WITH DUPLICATES
               ALTERNATE RECORD KEY IS D-UNQ
               FILE STATUS IS ST.
           SELECT SFILE ASSIGN TO "dseq.dat"
               ORGANIZATION IS INDEXED
               ACCESS MODE IS SEQUENTIAL
               RECORD KEY IS S-KEY
               FILE STATUS IS SS.
       DATA DIVISION.
       FILE SECTION.
       FD DFILE.
       01 D-REC.
          05 D-KEY.
             10 D-K1 PIC X.
             10 D-K2 PIC X.
          05 D-GRP   PIC X.
          05 D-UNQ   PIC X(2).
          05 D-VAL   PIC 9(3).
       FD SFILE.
       01 S-REC.
          05 S-KEY   PIC X.
          05 S-VAL   PIC X.
       WORKING-STORAGE SECTION.
       01 ST PIC XX.
       01 SS PIC XX.
       PROCEDURE DIVISION.
           OPEN OUTPUT DFILE. CLOSE DFILE.
           OPEN I-O DFILE. DISPLAY "open " ST.
           MOVE "X" TO D-GRP.
           READ DFILE KEY IS D-GRP.
           DISPLAY "000 RG " ST " " D-REC.
           OPEN INPUT SFILE.
           DISPLAY "000S OPEN " SS " " S-REC.
           MOVE "Q2" TO D-UNQ.
           START DFILE KEY >= D-UNQ.
           DISPLAY "001 ST " ST " " D-REC.
           MOVE "BCXQ2594" TO D-REC.
           WRITE D-REC.
           DISPLAY "002 W " ST " " D-REC.
           MOVE "CAXP1263" TO D-REC.
           WRITE D-REC.
           DISPLAY "003 W " ST " " D-REC.
           CLOSE DFILE. OPEN I-O DFILE.
           DISPLAY "004 REOPEN " ST " " D-REC.
           MOVE "BAYR1474" TO D-REC.
           WRITE D-REC.
           DISPLAY "005 W " ST " " D-REC.
           MOVE "AB" TO D-KEY.
           DELETE DFILE.
           DISPLAY "006 DEL " ST " " D-REC.
           CLOSE DFILE. OPEN I-O DFILE.
           DISPLAY "007 REOPEN " ST " " D-REC.
           MOVE "CAYP2984" TO D-REC.
           REWRITE D-REC.
           DISPLAY "008 RW " ST " " D-REC.
           MOVE "S1" TO D-UNQ.
           START DFILE KEY >= D-UNQ.
           DISPLAY "009 ST " ST " " D-REC.
           CLOSE SFILE.
           OPEN OUTPUT SFILE.
           DISPLAY "009S OPEN " SS " " S-REC.
           MOVE "AC" TO D-KEY.
           READ DFILE.
           DISPLAY "010 RK " ST " " D-REC.
           MOVE "AA" TO D-KEY.
           READ DFILE.
           DISPLAY "011 RK " ST " " D-REC.
           READ DFILE PREVIOUS.
           DISPLAY "012 RP " ST " " D-REC.
           MOVE "ACXQ2692" TO D-REC.
           WRITE D-REC.
           DISPLAY "013 W " ST " " D-REC.
           MOVE "BC" TO D-KEY.
           READ DFILE.
           DISPLAY "014 RK " ST " " D-REC.
           MOVE "CAYQ1230" TO D-REC.
           REWRITE D-REC.
           DISPLAY "015 RW " ST " " D-REC.
           MOVE "R1" TO D-UNQ.
           READ DFILE KEY IS D-UNQ.
           DISPLAY "016 RU " ST " " D-REC.
           MOVE "CCXQ1203" TO D-REC.
           REWRITE D-REC.
           DISPLAY "017 RW " ST " " D-REC.
           READ SFILE.
           DISPLAY "017S R " SS " " S-REC.
           MOVE "BCYP2435" TO D-REC.
           WRITE D-REC.
           DISPLAY "018 W " ST " " D-REC.
           MOVE "X" TO D-GRP.
           READ DFILE KEY IS D-GRP.
           DISPLAY "019 RG " ST " " D-REC.
           MOVE "D" TO D-K1.
           START DFILE KEY > D-K1.
           DISPLAY "020 ST " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "021 RN " ST " " D-REC.
           MOVE "ACXR1740" TO D-REC.
           REWRITE D-REC.
           DISPLAY "022 RW " ST " " D-REC.
           MOVE "CB" TO D-KEY.
           READ DFILE.
           DISPLAY "023 RK " ST " " D-REC.
           MOVE "Y" TO D-GRP.
           READ DFILE KEY IS D-GRP.
           DISPLAY "024 RG " ST " " D-REC.
           MOVE "ABYQ2009" TO D-REC.
           REWRITE D-REC.
           DISPLAY "025 RW " ST " " D-REC.
           MOVE "8z" TO S-REC.
           REWRITE S-REC.
           DISPLAY "025S RW " SS " " S-REC.
           MOVE "CAXP1993" TO D-REC.
           WRITE D-REC.
           DISPLAY "026 W " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "027 RN " ST " " D-REC.
           MOVE "BA" TO D-KEY.
           READ DFILE.
           DISPLAY "028 RK " ST " " D-REC.
           MOVE "3c" TO S-REC.
           WRITE S-REC.
           DISPLAY "028S W " SS " " S-REC.
           READ DFILE PREVIOUS.
           DISPLAY "029 RP " ST " " D-REC.
           READ SFILE.
           DISPLAY "029S R " SS " " S-REC.
           MOVE "AAXP2769" TO D-REC.
           WRITE D-REC.
           DISPLAY "030 W " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "031 RN " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "032 RN " ST " " D-REC.
           MOVE "0" TO S-KEY.
           START SFILE KEY <= S-KEY.
           DISPLAY "032S ST " SS " " S-REC.
           MOVE "X" TO D-GRP.
           READ DFILE KEY IS D-GRP.
           DISPLAY "033 RG " ST " " D-REC.
           MOVE "AA" TO D-KEY.
           READ DFILE.
           DISPLAY "034 RK " ST " " D-REC.
           READ SFILE.
           DISPLAY "034S R " SS " " S-REC.
           MOVE "Q1" TO D-UNQ.
           START DFILE KEY NOT < D-UNQ.
           DISPLAY "035 ST " ST " " D-REC.
           MOVE "Y" TO D-GRP.
           READ DFILE KEY IS D-GRP.
           DISPLAY "036 RG " ST " " D-REC.
           CLOSE DFILE. OPEN INPUT DFILE.
           READ DFILE NEXT.
           DISPLAY "037 IN " ST " " D-REC.
           MOVE "AAX" TO D-REC. WRITE D-REC.
           DISPLAY "037 INW " ST " " D-REC.
           CLOSE DFILE. OPEN I-O DFILE.
           READ DFILE NEXT.
           DISPLAY "038 RN " ST " " D-REC.
           MOVE "X" TO D-GRP.
           READ DFILE KEY IS D-GRP.
           DISPLAY "039 RG " ST " " D-REC.
           MOVE "Y" TO D-GRP.
           READ DFILE KEY IS D-GRP.
           DISPLAY "040 RG " ST " " D-REC.
           CLOSE DFILE. OPEN INPUT DFILE.
           READ DFILE NEXT.
           DISPLAY "041 IN " ST " " D-REC.
           MOVE "AAX" TO D-REC. WRITE D-REC.
           DISPLAY "041 INW " ST " " D-REC.
           CLOSE DFILE. OPEN I-O DFILE.
           MOVE "AAXR1443" TO D-REC.
           WRITE D-REC.
           DISPLAY "042 W " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "043 RN " ST " " D-REC.
           MOVE "C" TO D-K1.
           START DFILE KEY >= D-K1.
           DISPLAY "044 ST " ST " " D-REC.
           READ DFILE PREVIOUS.
           DISPLAY "045 RP " ST " " D-REC.
           READ DFILE PREVIOUS.
           DISPLAY "046 RP " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "047 RN " ST " " D-REC.
           MOVE "CCXP2518" TO D-REC.
           WRITE D-REC.
           DISPLAY "048 W " ST " " D-REC.
           READ SFILE.
           DISPLAY "048S R " SS " " S-REC.
           MOVE "BA" TO D-KEY.
           DELETE DFILE.
           DISPLAY "049 DEL " ST " " D-REC.
           MOVE "1b" TO S-REC.
           WRITE S-REC.
           DISPLAY "049S W " SS " " S-REC.
           MOVE "B@" TO D-KEY.
           START DFILE KEY > D-KEY.
           DISPLAY "050 ST " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "051 RN " ST " " D-REC.
           MOVE "BBXR1790" TO D-REC.
           WRITE D-REC.
           DISPLAY "052 W " ST " " D-REC.
           MOVE "CCYP1590" TO D-REC.
           REWRITE D-REC.
           DISPLAY "053 RW " ST " " D-REC.
           MOVE "5" TO S-KEY.
           START SFILE KEY < S-KEY.
           DISPLAY "053S ST " SS " " S-REC.
           MOVE "CA" TO D-KEY.
           DELETE DFILE.
           DISPLAY "054 DEL " ST " " D-REC.
           MOVE "3x" TO S-REC.
           REWRITE S-REC.
           DISPLAY "054S RW " SS " " S-REC.
           READ DFILE NEXT.
           DISPLAY "055 RN " ST " " D-REC.
           MOVE "BC" TO D-KEY.
           READ DFILE.
           DISPLAY "056 RK " ST " " D-REC.
           MOVE "CC" TO D-KEY.
           READ DFILE.
           DISPLAY "057 RK " ST " " D-REC.
           CLOSE DFILE. OPEN I-O DFILE.
           DISPLAY "058 REOPEN " ST " " D-REC.
           MOVE "AAXQ2481" TO D-REC.
           WRITE D-REC.
           DISPLAY "059 W " ST " " D-REC.
           CLOSE DFILE. OPEN I-O DFILE.
           DISPLAY "060 REOPEN " ST " " D-REC.
           MOVE "S1" TO D-UNQ.
           START DFILE KEY = D-UNQ.
           DISPLAY "061 ST " ST " " D-REC.
           READ DFILE PREVIOUS.
           DISPLAY "062 RP " ST " " D-REC.
           MOVE "CCXQ1396" TO D-REC.
           WRITE D-REC.
           DISPLAY "063 W " ST " " D-REC.
           MOVE "BAXQ1093" TO D-REC.
           WRITE D-REC.
           DISPLAY "064 W " ST " " D-REC.
           MOVE "AA" TO D-KEY.
           START DFILE KEY NOT > D-KEY.
           DISPLAY "065 ST " ST " " D-REC.
           MOVE "BBYP1329" TO D-REC.
           REWRITE D-REC.
           DISPLAY "066 RW " ST " " D-REC.
           MOVE "A" TO D-GRP.
           START DFILE KEY < D-GRP.
           DISPLAY "067 ST " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "068 RN " ST " " D-REC.
           CLOSE SFILE.
           OPEN OUTPUT SFILE.
           DISPLAY "068S OPEN " SS " " S-REC.
           READ DFILE NEXT.
           DISPLAY "069 RN " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "070 RN " ST " " D-REC.
           MOVE "R1" TO D-UNQ.
           START DFILE KEY NOT > D-UNQ.
           DISPLAY "071 ST " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "072 RN " ST " " D-REC.
           MOVE "BC" TO D-KEY.
           READ DFILE.
           DISPLAY "073 RK " ST " " D-REC.
           CLOSE SFILE.
           OPEN OUTPUT SFILE.
           DISPLAY "073S OPEN " SS " " S-REC.
           MOVE "A" TO D-GRP.
           START DFILE KEY NOT > D-GRP.
           DISPLAY "074 ST " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "075 RN " ST " " D-REC.
           MOVE "B" TO D-K1.
           START DFILE KEY > D-K1.
           DISPLAY "076 ST " ST " " D-REC.
           MOVE "BAYQ1498" TO D-REC.
           WRITE D-REC.
           DISPLAY "077 W " ST " " D-REC.
           MOVE "Z" TO D-GRP.
           START DFILE KEY > D-GRP.
           DISPLAY "078 ST " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "079 RN " ST " " D-REC.
           MOVE "AC" TO D-KEY.
           READ DFILE.
           DISPLAY "080 RK " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "081 RN " ST " " D-REC.
           MOVE "CAYQ1915" TO D-REC.
           WRITE D-REC.
           DISPLAY "082 W " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "083 RN " ST " " D-REC.
           READ SFILE.
           DISPLAY "083S R " SS " " S-REC.
           MOVE "P2" TO D-UNQ.
           READ DFILE KEY IS D-UNQ.
           DISPLAY "084 RU " ST " " D-REC.
           MOVE "1y" TO S-REC.
           REWRITE S-REC.
           DISPLAY "084S RW " SS " " S-REC.
           MOVE "ABXP2766" TO D-REC.
           WRITE D-REC.
           DISPLAY "085 W " ST " " D-REC.
           MOVE "Y" TO D-GRP.
           READ DFILE KEY IS D-GRP.
           DISPLAY "086 RG " ST " " D-REC.
           READ DFILE PREVIOUS.
           DISPLAY "087 RP " ST " " D-REC.
           MOVE "BCXR1150" TO D-REC.
           REWRITE D-REC.
           DISPLAY "088 RW " ST " " D-REC.
           READ SFILE.
           DISPLAY "088S R " SS " " S-REC.
           MOVE "BAYQ1354" TO D-REC.
           WRITE D-REC.
           DISPLAY "089 W " ST " " D-REC.
           MOVE "BCXQ2571" TO D-REC.
           REWRITE D-REC.
           DISPLAY "090 RW " ST " " D-REC.
           MOVE "X" TO D-GRP.
           READ DFILE KEY IS D-GRP.
           DISPLAY "091 RG " ST " " D-REC.
           READ SFILE.
           DISPLAY "091S R " SS " " S-REC.
           MOVE "C" TO D-K1.
           START DFILE KEY NOT < D-K1.
           DISPLAY "092 ST " ST " " D-REC.
           READ DFILE PREVIOUS.
           DISPLAY "093 RP " ST " " D-REC.
           MOVE "P1" TO D-UNQ.
           START DFILE KEY <= D-UNQ.
           DISPLAY "094 ST " ST " " D-REC.
           MOVE "X" TO D-GRP.
           START DFILE KEY >= D-GRP.
           DISPLAY "095 ST " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "096 RN " ST " " D-REC.
           MOVE "CBYQ2683" TO D-REC.
           WRITE D-REC.
           DISPLAY "097 W " ST " " D-REC.
           MOVE "Y" TO D-GRP.
           START DFILE KEY >= D-GRP.
           DISPLAY "098 ST " ST " " D-REC.
           MOVE "CCYP2485" TO D-REC.
           REWRITE D-REC.
           DISPLAY "099 RW " ST " " D-REC.
           CLOSE SFILE.
           OPEN EXTEND SFILE.
           DISPLAY "099S OPEN " SS " " S-REC.
           MOVE "ABYR1020" TO D-REC.
           WRITE D-REC.
           DISPLAY "100 W " ST " " D-REC.
           MOVE "1b" TO S-REC.
           WRITE S-REC.
           DISPLAY "100S W " SS " " S-REC.
           MOVE "P2" TO D-UNQ.
           START DFILE KEY = D-UNQ.
           DISPLAY "101 ST " ST " " D-REC.
           CLOSE DFILE. OPEN INPUT DFILE.
           READ DFILE NEXT.
           DISPLAY "102 IN " ST " " D-REC.
           MOVE "AAX" TO D-REC. WRITE D-REC.
           DISPLAY "102 INW " ST " " D-REC.
           CLOSE DFILE. OPEN I-O DFILE.
           CLOSE DFILE. OPEN INPUT DFILE.
           READ DFILE NEXT.
           DISPLAY "103 IN " ST " " D-REC.
           MOVE "AAX" TO D-REC. WRITE D-REC.
           DISPLAY "103 INW " ST " " D-REC.
           CLOSE DFILE. OPEN I-O DFILE.
           MOVE "BBXQ1905" TO D-REC.
           WRITE D-REC.
           DISPLAY "104 W " ST " " D-REC.
           CLOSE DFILE. OPEN INPUT DFILE.
           READ DFILE NEXT.
           DISPLAY "105 IN " ST " " D-REC.
           MOVE "AAX" TO D-REC. WRITE D-REC.
           DISPLAY "105 INW " ST " " D-REC.
           CLOSE DFILE. OPEN I-O DFILE.
           MOVE "CB" TO D-KEY.
           READ DFILE.
           DISPLAY "106 RK " ST " " D-REC.
           READ SFILE.
           DISPLAY "106S R " SS " " S-REC.
           MOVE "X" TO D-GRP.
           READ DFILE KEY IS D-GRP.
           DISPLAY "107 RG " ST " " D-REC.
           CLOSE SFILE.
           OPEN OUTPUT SFILE.
           DISPLAY "107S OPEN " SS " " S-REC.
           MOVE "Q1" TO D-UNQ.
           START DFILE KEY = D-UNQ.
           DISPLAY "108 ST " ST " " D-REC.
           MOVE "BCXQ1775" TO D-REC.
           WRITE D-REC.
           DISPLAY "109 W " ST " " D-REC.
           MOVE "Q2" TO D-UNQ.
           READ DFILE KEY IS D-UNQ.
           DISPLAY "110 RU " ST " " D-REC.
           MOVE "BCYQ2954" TO D-REC.
           WRITE D-REC.
           DISPLAY "111 W " ST " " D-REC.
           MOVE "Q2" TO D-UNQ.
           START DFILE KEY NOT > D-UNQ.
           DISPLAY "112 ST " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "113 RN " ST " " D-REC.
           MOVE "CC" TO D-KEY.
           DELETE DFILE.
           DISPLAY "114 DEL " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "115 RN " ST " " D-REC.
           CLOSE SFILE.
           OPEN I-O SFILE.
           DISPLAY "115S OPEN " SS " " S-REC.
           MOVE "BC" TO D-KEY.
           DELETE DFILE.
           DISPLAY "116 DEL " ST " " D-REC.
           MOVE "AAYP1174" TO D-REC.
           WRITE D-REC.
           DISPLAY "117 W " ST " " D-REC.
           READ DFILE PREVIOUS.
           DISPLAY "118 RP " ST " " D-REC.
           MOVE "CCYQ2443" TO D-REC.
           WRITE D-REC.
           DISPLAY "119 W " ST " " D-REC.
           MOVE "Q1" TO D-UNQ.
           READ DFILE KEY IS D-UNQ.
           DISPLAY "120 RU " ST " " D-REC.
           MOVE "R1" TO D-UNQ.
           START DFILE KEY < D-UNQ.
           DISPLAY "121 ST " ST " " D-REC.
           CLOSE SFILE.
           OPEN I-O SFILE.
           DISPLAY "121S OPEN " SS " " S-REC.
           MOVE "BB" TO D-KEY.
           DELETE DFILE.
           DISPLAY "122 DEL " ST " " D-REC.
           MOVE "D" TO D-K1.
           START DFILE KEY <= D-K1.
           DISPLAY "123 ST " ST " " D-REC.
           READ SFILE.
           DISPLAY "123S R " SS " " S-REC.
           READ DFILE PREVIOUS.
           DISPLAY "124 RP " ST " " D-REC.
           MOVE "1z" TO S-REC.
           REWRITE S-REC.
           DISPLAY "124S RW " SS " " S-REC.
           MOVE "P2" TO D-UNQ.
           START DFILE KEY = D-UNQ.
           DISPLAY "125 ST " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "126 RN " ST " " D-REC.
           MOVE "CCYP1417" TO D-REC.
           REWRITE D-REC.
           DISPLAY "127 RW " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "128 RN " ST " " D-REC.
           MOVE "AB" TO D-KEY.
           READ DFILE.
           DISPLAY "129 RK " ST " " D-REC.
           DELETE SFILE.
           DISPLAY "129S DEL " SS " " S-REC.
           MOVE "Y" TO D-GRP.
           READ DFILE KEY IS D-GRP.
           DISPLAY "130 RG " ST " " D-REC.
           CLOSE DFILE. OPEN I-O DFILE.
           DISPLAY "131 REOPEN " ST " " D-REC.
           MOVE "Q1" TO D-UNQ.
           START DFILE KEY <= D-UNQ.
           DISPLAY "132 ST " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "133 RN " ST " " D-REC.
           MOVE "C@" TO D-KEY.
           START DFILE KEY NOT > D-KEY.
           DISPLAY "134 ST " ST " " D-REC.
           MOVE "ACYQ2215" TO D-REC.
           REWRITE D-REC.
           DISPLAY "135 RW " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "136 RN " ST " " D-REC.
           CLOSE DFILE. OPEN I-O DFILE.
           DISPLAY "137 REOPEN " ST " " D-REC.
           MOVE "AAXP1887" TO D-REC.
           WRITE D-REC.
           DISPLAY "138 W " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "139 RN " ST " " D-REC.
           MOVE "X" TO D-GRP.
           READ DFILE KEY IS D-GRP.
           DISPLAY "140 RG " ST " " D-REC.
           MOVE "A" TO D-K1.
           START DFILE KEY <= D-K1.
           DISPLAY "141 ST " ST " " D-REC.
           CLOSE SFILE.
           OPEN EXTEND SFILE.
           DISPLAY "141S OPEN " SS " " S-REC.
           READ DFILE PREVIOUS.
           DISPLAY "142 RP " ST " " D-REC.
           MOVE "9y" TO S-REC.
           REWRITE S-REC.
           DISPLAY "142S RW " SS " " S-REC.
           MOVE "Y" TO D-GRP.
           START DFILE KEY < D-GRP.
           DISPLAY "143 ST " ST " " D-REC.
           READ SFILE.
           DISPLAY "143S R " SS " " S-REC.
           MOVE "P2" TO D-UNQ.
           READ DFILE KEY IS D-UNQ.
           DISPLAY "144 RU " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "145 RN " ST " " D-REC.
           READ DFILE PREVIOUS.
           DISPLAY "146 RP " ST " " D-REC.
           MOVE "BAYR1547" TO D-REC.
           WRITE D-REC.
           DISPLAY "147 W " ST " " D-REC.
           MOVE "BCXP2785" TO D-REC.
           WRITE D-REC.
           DISPLAY "148 W " ST " " D-REC.
           MOVE "5c" TO S-REC.
           WRITE S-REC.
           DISPLAY "148S W " SS " " S-REC.
           MOVE "S1" TO D-UNQ.
           START DFILE KEY > D-UNQ.
           DISPLAY "149 ST " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "150 RN " ST " " D-REC.
           READ SFILE.
           DISPLAY "150S R " SS " " S-REC.
           MOVE "X" TO D-GRP.
           READ DFILE KEY IS D-GRP.
           DISPLAY "151 RG " ST " " D-REC.
           READ DFILE PREVIOUS.
           DISPLAY "152 RP " ST " " D-REC.
           MOVE "BC" TO D-KEY.
           READ DFILE.
           DISPLAY "153 RK " ST " " D-REC.
           MOVE "CAYR1100" TO D-REC.
           REWRITE D-REC.
           DISPLAY "154 RW " ST " " D-REC.
           READ DFILE PREVIOUS.
           DISPLAY "155 RP " ST " " D-REC.
           MOVE "6z" TO S-REC.
           REWRITE S-REC.
           DISPLAY "155S RW " SS " " S-REC.
           MOVE "BCXP1760" TO D-REC.
           WRITE D-REC.
           DISPLAY "156 W " ST " " D-REC.
           MOVE "CCYP1151" TO D-REC.
           REWRITE D-REC.
           DISPLAY "157 RW " ST " " D-REC.
           CLOSE SFILE.
           OPEN EXTEND SFILE.
           DISPLAY "157S OPEN " SS " " S-REC.
           READ DFILE NEXT.
           DISPLAY "158 RN " ST " " D-REC.
           MOVE "BB" TO D-KEY.
           START DFILE KEY NOT > D-KEY.
           DISPLAY "159 ST " ST " " D-REC.
           MOVE "8y" TO S-REC.
           REWRITE S-REC.
           DISPLAY "159S RW " SS " " S-REC.
           CLOSE DFILE. OPEN INPUT DFILE.
           READ DFILE NEXT.
           DISPLAY "160 IN " ST " " D-REC.
           MOVE "AAX" TO D-REC. WRITE D-REC.
           DISPLAY "160 INW " ST " " D-REC.
           CLOSE DFILE. OPEN I-O DFILE.
           MOVE "CAYP1919" TO D-REC.
           REWRITE D-REC.
           DISPLAY "161 RW " ST " " D-REC.
           MOVE "BB" TO D-KEY.
           START DFILE KEY >= D-KEY.
           DISPLAY "162 ST " ST " " D-REC.
           MOVE "CCYP1827" TO D-REC.
           WRITE D-REC.
           DISPLAY "163 W " ST " " D-REC.
           READ SFILE.
           DISPLAY "163S R " SS " " S-REC.
           READ DFILE PREVIOUS.
           DISPLAY "164 RP " ST " " D-REC.
           CLOSE DFILE. OPEN I-O DFILE.
           DISPLAY "165 REOPEN " ST " " D-REC.
           MOVE "9b" TO S-REC.
           WRITE S-REC.
           DISPLAY "165S W " SS " " S-REC.
           MOVE "Y" TO D-GRP.
           START DFILE KEY NOT < D-GRP.
           DISPLAY "166 ST " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "167 RN " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "168 RN " ST " " D-REC.
           READ DFILE PREVIOUS.
           DISPLAY "169 RP " ST " " D-REC.
           MOVE "C" TO D-K1.
           START DFILE KEY < D-K1.
           DISPLAY "170 ST " ST " " D-REC.
           MOVE "ACYQ1972" TO D-REC.
           WRITE D-REC.
           DISPLAY "171 W " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "172 RN " ST " " D-REC.
           MOVE "9c" TO S-REC.
           WRITE S-REC.
           DISPLAY "172S W " SS " " S-REC.
           MOVE "CBYP1974" TO D-REC.
           WRITE D-REC.
           DISPLAY "173 W " ST " " D-REC.
           MOVE "ACYP2780" TO D-REC.
           REWRITE D-REC.
           DISPLAY "174 RW " ST " " D-REC.
           MOVE "2y" TO S-REC.
           REWRITE S-REC.
           DISPLAY "174S RW " SS " " S-REC.
           MOVE "ABXQ1396" TO D-REC.
           WRITE D-REC.
           DISPLAY "175 W " ST " " D-REC.
           MOVE "8" TO S-KEY.
           START SFILE KEY < S-KEY.
           DISPLAY "175S ST " SS " " S-REC.
           READ DFILE NEXT.
           DISPLAY "176 RN " ST " " D-REC.
           MOVE "ABYR1118" TO D-REC.
           REWRITE D-REC.
           DISPLAY "177 RW " ST " " D-REC.
           MOVE "CC" TO D-KEY.
           READ DFILE.
           DISPLAY "178 RK " ST " " D-REC.
           MOVE "CCYP1470" TO D-REC.
           WRITE D-REC.
           DISPLAY "179 W " ST " " D-REC.
           CLOSE SFILE.
           OPEN EXTEND SFILE.
           DISPLAY "179S OPEN " SS " " S-REC.
           READ DFILE NEXT.
           DISPLAY "180 RN " ST " " D-REC.
           MOVE "Y" TO D-GRP.
           READ DFILE KEY IS D-GRP.
           DISPLAY "181 RG " ST " " D-REC.
           MOVE "4a" TO S-REC.
           WRITE S-REC.
           DISPLAY "181S W " SS " " S-REC.
           MOVE "CAXQ1319" TO D-REC.
           REWRITE D-REC.
           DISPLAY "182 RW " ST " " D-REC.
           MOVE "0" TO S-KEY.
           START SFILE KEY < S-KEY.
           DISPLAY "182S ST " SS " " S-REC.
           MOVE "R1" TO D-UNQ.
           START DFILE KEY NOT > D-UNQ.
           DISPLAY "183 ST " ST " " D-REC.
           MOVE "P0" TO D-UNQ.
           START DFILE KEY NOT > D-UNQ.
           DISPLAY "184 ST " ST " " D-REC.
           MOVE "X" TO D-GRP.
           READ DFILE KEY IS D-GRP.
           DISPLAY "185 RG " ST " " D-REC.
           MOVE "X" TO D-GRP.
           START DFILE KEY NOT > D-GRP.
           DISPLAY "186 ST " ST " " D-REC.
           MOVE "B" TO D-K1.
           START DFILE KEY NOT > D-K1.
           DISPLAY "187 ST " ST " " D-REC.
           MOVE "CCXQ1627" TO D-REC.
           REWRITE D-REC.
           DISPLAY "188 RW " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "189 RN " ST " " D-REC.
           MOVE "9z" TO S-REC.
           REWRITE S-REC.
           DISPLAY "189S RW " SS " " S-REC.
           READ DFILE PREVIOUS.
           DISPLAY "190 RP " ST " " D-REC.
           CLOSE SFILE.
           OPEN EXTEND SFILE.
           DISPLAY "190S OPEN " SS " " S-REC.
           READ DFILE PREVIOUS.
           DISPLAY "191 RP " ST " " D-REC.
           MOVE "6y" TO S-REC.
           REWRITE S-REC.
           DISPLAY "191S RW " SS " " S-REC.
           READ DFILE NEXT.
           DISPLAY "192 RN " ST " " D-REC.
           READ DFILE PREVIOUS.
           DISPLAY "193 RP " ST " " D-REC.
           CLOSE SFILE.
           OPEN INPUT SFILE.
           DISPLAY "193S OPEN " SS " " S-REC.
           MOVE "CCYP2162" TO D-REC.
           WRITE D-REC.
           DISPLAY "194 W " ST " " D-REC.
           CLOSE SFILE.
           OPEN I-O SFILE.
           DISPLAY "194S OPEN " SS " " S-REC.
           CLOSE DFILE. OPEN I-O DFILE.
           DISPLAY "195 REOPEN " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "196 RN " ST " " D-REC.
           CLOSE SFILE.
           OPEN I-O SFILE.
           DISPLAY "196S OPEN " SS " " S-REC.
           MOVE "X" TO D-GRP.
           READ DFILE KEY IS D-GRP.
           DISPLAY "197 RG " ST " " D-REC.
           MOVE "D" TO D-K1.
           START DFILE KEY <= D-K1.
           DISPLAY "198 ST " ST " " D-REC.
           READ DFILE PREVIOUS.
           DISPLAY "199 RP " ST " " D-REC.
           READ DFILE PREVIOUS.
           DISPLAY "200 RP " ST " " D-REC.
           READ DFILE PREVIOUS.
           DISPLAY "201 RP " ST " " D-REC.
           MOVE "X" TO D-GRP.
           READ DFILE KEY IS D-GRP.
           DISPLAY "202 RG " ST " " D-REC.
           MOVE "A@" TO D-KEY.
           START DFILE KEY = D-KEY.
           DISPLAY "203 ST " ST " " D-REC.
           MOVE "P0" TO D-UNQ.
           START DFILE KEY > D-UNQ.
           DISPLAY "204 ST " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "205 RN " ST " " D-REC.
           MOVE "CAXP2540" TO D-REC.
           WRITE D-REC.
           DISPLAY "206 W " ST " " D-REC.
           MOVE "P2" TO D-UNQ.
           START DFILE KEY NOT > D-UNQ.
           DISPLAY "207 ST " ST " " D-REC.
           MOVE "BA" TO D-KEY.
           START DFILE KEY < D-KEY.
           DISPLAY "208 ST " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "209 RN " ST " " D-REC.
           MOVE "CC" TO D-KEY.
           START DFILE KEY >= D-KEY.
           DISPLAY "210 ST " ST " " D-REC.
           READ SFILE.
           DISPLAY "210S R " SS " " S-REC.
           READ DFILE PREVIOUS.
           DISPLAY "211 RP " ST " " D-REC.
           MOVE "ACXP1993" TO D-REC.
           WRITE D-REC.
           DISPLAY "212 W " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "213 RN " ST " " D-REC.
           DELETE SFILE.
           DISPLAY "213S DEL " SS " " S-REC.
           MOVE "X" TO D-GRP.
           READ DFILE KEY IS D-GRP.
           DISPLAY "214 RG " ST " " D-REC.
           READ DFILE PREVIOUS.
           DISPLAY "215 RP " ST " " D-REC.
           MOVE "Z" TO D-GRP.
           START DFILE KEY >= D-GRP.
           DISPLAY "216 ST " ST " " D-REC.
           MOVE "P1" TO D-UNQ.
           START DFILE KEY < D-UNQ.
           DISPLAY "217 ST " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "218 RN " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "219 RN " ST " " D-REC.
           MOVE "ABXQ2913" TO D-REC.
           WRITE D-REC.
           DISPLAY "220 W " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "221 RN " ST " " D-REC.
           MOVE "Q2" TO D-UNQ.
           START DFILE KEY = D-UNQ.
           DISPLAY "222 ST " ST " " D-REC.
           READ DFILE PREVIOUS.
           DISPLAY "223 RP " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "224 RN " ST " " D-REC.
           READ DFILE PREVIOUS.
           DISPLAY "225 RP " ST " " D-REC.
           MOVE "ACXP2576" TO D-REC.
           WRITE D-REC.
           DISPLAY "226 W " ST " " D-REC.
           MOVE "BAXP2124" TO D-REC.
           WRITE D-REC.
           DISPLAY "227 W " ST " " D-REC.
           MOVE "ACXP2075" TO D-REC.
           WRITE D-REC.
           DISPLAY "228 W " ST " " D-REC.
           MOVE "5x" TO S-REC.
           REWRITE S-REC.
           DISPLAY "228S RW " SS " " S-REC.
           MOVE "AAYR1314" TO D-REC.
           REWRITE D-REC.
           DISPLAY "229 RW " ST " " D-REC.
           MOVE "9c" TO S-REC.
           WRITE S-REC.
           DISPLAY "229S W " SS " " S-REC.
           MOVE "R1" TO D-UNQ.
           READ DFILE KEY IS D-UNQ.
           DISPLAY "230 RU " ST " " D-REC.
           MOVE "CA" TO D-KEY.
           DELETE DFILE.
           DISPLAY "231 DEL " ST " " D-REC.
           MOVE "Q2" TO D-UNQ.
           START DFILE KEY > D-UNQ.
           DISPLAY "232 ST " ST " " D-REC.
           MOVE "CC" TO D-KEY.
           READ DFILE.
           DISPLAY "233 RK " ST " " D-REC.
           CLOSE DFILE. OPEN INPUT DFILE.
           READ DFILE NEXT.
           DISPLAY "234 IN " ST " " D-REC.
           MOVE "AAX" TO D-REC. WRITE D-REC.
           DISPLAY "234 INW " ST " " D-REC.
           CLOSE DFILE. OPEN I-O DFILE.
           MOVE "BC" TO D-KEY.
           READ DFILE.
           DISPLAY "235 RK " ST " " D-REC.
           CLOSE SFILE.
           OPEN INPUT SFILE.
           DISPLAY "235S OPEN " SS " " S-REC.
           MOVE "X" TO D-GRP.
           START DFILE KEY >= D-GRP.
           DISPLAY "236 ST " ST " " D-REC.
           MOVE "C" TO D-K1.
           START DFILE KEY <= D-K1.
           DISPLAY "237 ST " ST " " D-REC.
           MOVE "D" TO D-K1.
           START DFILE KEY <= D-K1.
           DISPLAY "238 ST " ST " " D-REC.
           DELETE SFILE.
           DISPLAY "238S DEL " SS " " S-REC.
           CLOSE DFILE. OPEN I-O DFILE.
           DISPLAY "239 REOPEN " ST " " D-REC.
           MOVE "CCXR1174" TO D-REC.
           WRITE D-REC.
           DISPLAY "240 W " ST " " D-REC.
           MOVE "6c" TO S-REC.
           WRITE S-REC.
           DISPLAY "240S W " SS " " S-REC.
           CLOSE DFILE. OPEN I-O DFILE.
           DISPLAY "241 REOPEN " ST " " D-REC.
           MOVE "BC" TO D-KEY.
           READ DFILE.
           DISPLAY "242 RK " ST " " D-REC.
           MOVE "Z" TO D-GRP.
           START DFILE KEY < D-GRP.
           DISPLAY "243 ST " ST " " D-REC.
           MOVE "P2" TO D-UNQ.
           START DFILE KEY >= D-UNQ.
           DISPLAY "244 ST " ST " " D-REC.
           READ SFILE.
           DISPLAY "244S R " SS " " S-REC.
           READ DFILE PREVIOUS.
           DISPLAY "245 RP " ST " " D-REC.
           MOVE "BBXQ1467" TO D-REC.
           WRITE D-REC.
           DISPLAY "246 W " ST " " D-REC.
           MOVE "9b" TO S-REC.
           WRITE S-REC.
           DISPLAY "246S W " SS " " S-REC.
           MOVE "BB" TO D-KEY.
           START DFILE KEY < D-KEY.
           DISPLAY "247 ST " ST " " D-REC.
           MOVE "CAYP1317" TO D-REC.
           WRITE D-REC.
           DISPLAY "248 W " ST " " D-REC.
           MOVE "S1" TO D-UNQ.
           START DFILE KEY > D-UNQ.
           DISPLAY "249 ST " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "250 RN " ST " " D-REC.
           MOVE "AAYP2616" TO D-REC.
           REWRITE D-REC.
           DISPLAY "251 RW " ST " " D-REC.
           READ DFILE PREVIOUS.
           DISPLAY "252 RP " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "253 RN " ST " " D-REC.
           READ DFILE PREVIOUS.
           DISPLAY "254 RP " ST " " D-REC.
           CLOSE DFILE. OPEN INPUT DFILE.
           READ DFILE NEXT.
           DISPLAY "255 IN " ST " " D-REC.
           MOVE "AAX" TO D-REC. WRITE D-REC.
           DISPLAY "255 INW " ST " " D-REC.
           CLOSE DFILE. OPEN I-O DFILE.
           MOVE "Z" TO D-GRP.
           START DFILE KEY >= D-GRP.
           DISPLAY "256 ST " ST " " D-REC.
           MOVE "BC" TO D-KEY.
           DELETE DFILE.
           DISPLAY "257 DEL " ST " " D-REC.
           CLOSE DFILE. OPEN I-O DFILE.
           DISPLAY "258 REOPEN " ST " " D-REC.
           MOVE "Q2" TO D-UNQ.
           START DFILE KEY <= D-UNQ.
           DISPLAY "259 ST " ST " " D-REC.
           MOVE "8" TO S-KEY.
           START SFILE KEY > S-KEY.
           DISPLAY "259S ST " SS " " S-REC.
           MOVE "AB" TO D-KEY.
           START DFILE KEY > D-KEY.
           DISPLAY "260 ST " ST " " D-REC.
           MOVE "AA" TO D-KEY.
           READ DFILE.
           DISPLAY "261 RK " ST " " D-REC.
           MOVE "BBYP2286" TO D-REC.
           WRITE D-REC.
           DISPLAY "262 W " ST " " D-REC.
           MOVE "Q2" TO D-UNQ.
           READ DFILE KEY IS D-UNQ.
           DISPLAY "263 RU " ST " " D-REC.
           MOVE "CB" TO D-KEY.
           DELETE DFILE.
           DISPLAY "264 DEL " ST " " D-REC.
           READ DFILE PREVIOUS.
           DISPLAY "265 RP " ST " " D-REC.
           MOVE "CAXQ1575" TO D-REC.
           WRITE D-REC.
           DISPLAY "266 W " ST " " D-REC.
           MOVE "S1" TO D-UNQ.
           START DFILE KEY > D-UNQ.
           DISPLAY "267 ST " ST " " D-REC.
           MOVE "CBXR1630" TO D-REC.
           WRITE D-REC.
           DISPLAY "268 W " ST " " D-REC.
           CLOSE SFILE.
           OPEN I-O SFILE.
           DISPLAY "268S OPEN " SS " " S-REC.
           MOVE "BCXP1021" TO D-REC.
           WRITE D-REC.
           DISPLAY "269 W " ST " " D-REC.
           CLOSE SFILE.
           OPEN INPUT SFILE.
           DISPLAY "269S OPEN " SS " " S-REC.
           MOVE "Q2" TO D-UNQ.
           START DFILE KEY <= D-UNQ.
           DISPLAY "270 ST " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "271 RN " ST " " D-REC.
           MOVE "BB" TO D-KEY.
           READ DFILE.
           DISPLAY "272 RK " ST " " D-REC.
           MOVE "CC" TO D-KEY.
           READ DFILE.
           DISPLAY "273 RK " ST " " D-REC.
           MOVE "CBYR1551" TO D-REC.
           REWRITE D-REC.
           DISPLAY "274 RW " ST " " D-REC.
           MOVE "BB" TO D-KEY.
           START DFILE KEY >= D-KEY.
           DISPLAY "275 ST " ST " " D-REC.
           MOVE "CBYP2932" TO D-REC.
           REWRITE D-REC.
           DISPLAY "276 RW " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "277 RN " ST " " D-REC.
           MOVE "BB" TO D-KEY.
           DELETE DFILE.
           DISPLAY "278 DEL " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "279 RN " ST " " D-REC.
           READ DFILE PREVIOUS.
           DISPLAY "280 RP " ST " " D-REC.
           MOVE "BCXQ1033" TO D-REC.
           WRITE D-REC.
           DISPLAY "281 W " ST " " D-REC.
           MOVE "ACYP1654" TO D-REC.
           REWRITE D-REC.
           DISPLAY "282 RW " ST " " D-REC.
           MOVE "CBXR1476" TO D-REC.
           WRITE D-REC.
           DISPLAY "283 W " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "284 RN " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "285 RN " ST " " D-REC.
           CLOSE DFILE. OPEN INPUT DFILE.
           READ DFILE NEXT.
           DISPLAY "286 IN " ST " " D-REC.
           MOVE "AAX" TO D-REC. WRITE D-REC.
           DISPLAY "286 INW " ST " " D-REC.
           CLOSE DFILE. OPEN I-O DFILE.
           READ DFILE PREVIOUS.
           DISPLAY "287 RP " ST " " D-REC.
           MOVE "ABYQ2282" TO D-REC.
           WRITE D-REC.
           DISPLAY "288 W " ST " " D-REC.
           MOVE "P1" TO D-UNQ.
           START DFILE KEY = D-UNQ.
           DISPLAY "289 ST " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "290 RN " ST " " D-REC.
           MOVE "5c" TO S-REC.
           WRITE S-REC.
           DISPLAY "290S W " SS " " S-REC.
           CLOSE DFILE. OPEN I-O DFILE.
           DISPLAY "291 REOPEN " ST " " D-REC.
           READ SFILE.
           DISPLAY "291S R " SS " " S-REC.
           MOVE "Q1" TO D-UNQ.
           START DFILE KEY > D-UNQ.
           DISPLAY "292 ST " ST " " D-REC.
           CLOSE SFILE.
           OPEN INPUT SFILE.
           DISPLAY "292S OPEN " SS " " S-REC.
           MOVE "AB" TO D-KEY.
           DELETE DFILE.
           DISPLAY "293 DEL " ST " " D-REC.
           MOVE "5x" TO S-REC.
           REWRITE S-REC.
           DISPLAY "293S RW " SS " " S-REC.
           MOVE "BC" TO D-KEY.
           DELETE DFILE.
           DISPLAY "294 DEL " ST " " D-REC.
           READ DFILE PREVIOUS.
           DISPLAY "295 RP " ST " " D-REC.
           MOVE "AA" TO D-KEY.
           READ DFILE.
           DISPLAY "296 RK " ST " " D-REC.
           CLOSE DFILE. OPEN INPUT DFILE.
           READ DFILE NEXT.
           DISPLAY "297 IN " ST " " D-REC.
           MOVE "AAX" TO D-REC. WRITE D-REC.
           DISPLAY "297 INW " ST " " D-REC.
           CLOSE DFILE. OPEN I-O DFILE.
           CLOSE SFILE.
           OPEN INPUT SFILE.
           DISPLAY "297S OPEN " SS " " S-REC.
           MOVE "ABXQ2156" TO D-REC.
           WRITE D-REC.
           DISPLAY "298 W " ST " " D-REC.
           CLOSE DFILE. OPEN I-O DFILE.
           DISPLAY "299 REOPEN " ST " " D-REC.
           CLOSE DFILE. OPEN INPUT DFILE.
           READ DFILE NEXT.
           DISPLAY "300 IN " ST " " D-REC.
           MOVE "AAX" TO D-REC. WRITE D-REC.
           DISPLAY "300 INW " ST " " D-REC.
           CLOSE DFILE. OPEN I-O DFILE.
           MOVE "A" TO D-K1.
           START DFILE KEY >= D-K1.
           DISPLAY "301 ST " ST " " D-REC.
           CLOSE SFILE.
           OPEN OUTPUT SFILE.
           DISPLAY "301S OPEN " SS " " S-REC.
           READ DFILE PREVIOUS.
           DISPLAY "302 RP " ST " " D-REC.
           MOVE "CBYQ1733" TO D-REC.
           WRITE D-REC.
           DISPLAY "303 W " ST " " D-REC.
           MOVE "ABYR1609" TO D-REC.
           WRITE D-REC.
           DISPLAY "304 W " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "305 RN " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "306 RN " ST " " D-REC.
           DELETE SFILE.
           DISPLAY "306S DEL " SS " " S-REC.
           READ DFILE NEXT.
           DISPLAY "307 RN " ST " " D-REC.
           MOVE "C" TO D-K1.
           START DFILE KEY >= D-K1.
           DISPLAY "308 ST " ST " " D-REC.
           MOVE "7a" TO S-REC.
           WRITE S-REC.
           DISPLAY "308S W " SS " " S-REC.
           MOVE "CAYQ1460" TO D-REC.
           WRITE D-REC.
           DISPLAY "309 W " ST " " D-REC.
           MOVE "BCYR1618" TO D-REC.
           WRITE D-REC.
           DISPLAY "310 W " ST " " D-REC.
           MOVE "CB" TO D-KEY.
           READ DFILE.
           DISPLAY "311 RK " ST " " D-REC.
           MOVE "BB" TO D-KEY.
           DELETE DFILE.
           DISPLAY "312 DEL " ST " " D-REC.
           READ DFILE PREVIOUS.
           DISPLAY "313 RP " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "314 RN " ST " " D-REC.
           DELETE SFILE.
           DISPLAY "314S DEL " SS " " S-REC.
           MOVE "AA" TO D-KEY.
           DELETE DFILE.
           DISPLAY "315 DEL " ST " " D-REC.
           MOVE "A" TO D-GRP.
           START DFILE KEY NOT < D-GRP.
           DISPLAY "316 ST " ST " " D-REC.
           MOVE "BC" TO D-KEY.
           READ DFILE.
           DISPLAY "317 RK " ST " " D-REC.
           MOVE "6x" TO S-REC.
           REWRITE S-REC.
           DISPLAY "317S RW " SS " " S-REC.
           MOVE "CC" TO D-KEY.
           DELETE DFILE.
           DISPLAY "318 DEL " ST " " D-REC.
           MOVE "4" TO S-KEY.
           START SFILE KEY > S-KEY.
           DISPLAY "318S ST " SS " " S-REC.
           READ DFILE NEXT.
           DISPLAY "319 RN " ST " " D-REC.
           CLOSE SFILE.
           OPEN OUTPUT SFILE.
           DISPLAY "319S OPEN " SS " " S-REC.
           MOVE "Q1" TO D-UNQ.
           START DFILE KEY NOT > D-UNQ.
           DISPLAY "320 ST " ST " " D-REC.
           MOVE "7" TO S-KEY.
           START SFILE KEY = S-KEY.
           DISPLAY "320S ST " SS " " S-REC.
           MOVE "Q1" TO D-UNQ.
           START DFILE KEY <= D-UNQ.
           DISPLAY "321 ST " ST " " D-REC.
           MOVE "CBXQ2818" TO D-REC.
           WRITE D-REC.
           DISPLAY "322 W " ST " " D-REC.
           MOVE "CCYP2235" TO D-REC.
           WRITE D-REC.
           DISPLAY "323 W " ST " " D-REC.
           READ SFILE.
           DISPLAY "323S R " SS " " S-REC.
           READ DFILE PREVIOUS.
           DISPLAY "324 RP " ST " " D-REC.
           MOVE "AB" TO D-KEY.
           READ DFILE.
           DISPLAY "325 RK " ST " " D-REC.
           MOVE "ACYP2883" TO D-REC.
           WRITE D-REC.
           DISPLAY "326 W " ST " " D-REC.
           MOVE "A" TO D-GRP.
           START DFILE KEY NOT > D-GRP.
           DISPLAY "327 ST " ST " " D-REC.
           MOVE "CCXQ2548" TO D-REC.
           WRITE D-REC.
           DISPLAY "328 W " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "329 RN " ST " " D-REC.
           MOVE "5" TO S-KEY.
           START SFILE KEY > S-KEY.
           DISPLAY "329S ST " SS " " S-REC.
           MOVE "Q1" TO D-UNQ.
           START DFILE KEY NOT < D-UNQ.
           DISPLAY "330 ST " ST " " D-REC.
           MOVE "AB" TO D-KEY.
           START DFILE KEY <= D-KEY.
           DISPLAY "331 ST " ST " " D-REC.
           MOVE "X" TO D-GRP.
           READ DFILE KEY IS D-GRP.
           DISPLAY "332 RG " ST " " D-REC.
           MOVE "S1" TO D-UNQ.
           START DFILE KEY <= D-UNQ.
           DISPLAY "333 ST " ST " " D-REC.
           CLOSE SFILE.
           OPEN EXTEND SFILE.
           DISPLAY "333S OPEN " SS " " S-REC.
           MOVE "CA" TO D-KEY.
           START DFILE KEY NOT > D-KEY.
           DISPLAY "334 ST " ST " " D-REC.
           DELETE SFILE.
           DISPLAY "334S DEL " SS " " S-REC.
           MOVE "ACXQ1957" TO D-REC.
           WRITE D-REC.
           DISPLAY "335 W " ST " " D-REC.
           MOVE "CC" TO D-KEY.
           READ DFILE.
           DISPLAY "336 RK " ST " " D-REC.
           MOVE "9a" TO S-REC.
           WRITE S-REC.
           DISPLAY "336S W " SS " " S-REC.
           MOVE "BA" TO D-KEY.
           READ DFILE.
           DISPLAY "337 RK " ST " " D-REC.
           MOVE "BBXQ2062" TO D-REC.
           WRITE D-REC.
           DISPLAY "338 W " ST " " D-REC.
           MOVE "ACXQ1893" TO D-REC.
           WRITE D-REC.
           DISPLAY "339 W " ST " " D-REC.
           DELETE SFILE.
           DISPLAY "339S DEL " SS " " S-REC.
           READ DFILE PREVIOUS.
           DISPLAY "340 RP " ST " " D-REC.
           READ SFILE.
           DISPLAY "340S R " SS " " S-REC.
           CLOSE DFILE. OPEN INPUT DFILE.
           READ DFILE NEXT.
           DISPLAY "341 IN " ST " " D-REC.
           MOVE "AAX" TO D-REC. WRITE D-REC.
           DISPLAY "341 INW " ST " " D-REC.
           CLOSE DFILE. OPEN I-O DFILE.
           MOVE "CCXQ1343" TO D-REC.
           REWRITE D-REC.
           DISPLAY "342 RW " ST " " D-REC.
           MOVE "CAYP1400" TO D-REC.
           REWRITE D-REC.
           DISPLAY "343 RW " ST " " D-REC.
           MOVE "Q2" TO D-UNQ.
           READ DFILE KEY IS D-UNQ.
           DISPLAY "344 RU " ST " " D-REC.
           MOVE "ABYQ2317" TO D-REC.
           WRITE D-REC.
           DISPLAY "345 W " ST " " D-REC.
           MOVE "AA" TO D-KEY.
           READ DFILE.
           DISPLAY "346 RK " ST " " D-REC.
           MOVE "BAYP1378" TO D-REC.
           REWRITE D-REC.
           DISPLAY "347 RW " ST " " D-REC.
           MOVE "BB" TO D-KEY.
           DELETE DFILE.
           DISPLAY "348 DEL " ST " " D-REC.
           MOVE "6a" TO S-REC.
           WRITE S-REC.
           DISPLAY "348S W " SS " " S-REC.
           MOVE "@" TO D-K1.
           START DFILE KEY >= D-K1.
           DISPLAY "349 ST " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "350 RN " ST " " D-REC.
           MOVE "CB" TO D-KEY.
           READ DFILE.
           DISPLAY "351 RK " ST " " D-REC.
           READ SFILE.
           DISPLAY "351S R " SS " " S-REC.
           MOVE "CAYQ2774" TO D-REC.
           REWRITE D-REC.
           DISPLAY "352 RW " ST " " D-REC.
           MOVE "CBXQ1574" TO D-REC.
           REWRITE D-REC.
           DISPLAY "353 RW " ST " " D-REC.
           CLOSE SFILE.
           OPEN I-O SFILE.
           DISPLAY "353S OPEN " SS " " S-REC.
           READ DFILE NEXT.
           DISPLAY "354 RN " ST " " D-REC.
           MOVE "CB" TO D-KEY.
           READ DFILE.
           DISPLAY "355 RK " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "356 RN " ST " " D-REC.
           READ SFILE.
           DISPLAY "356S R " SS " " S-REC.
           MOVE "ABXP1621" TO D-REC.
           WRITE D-REC.
           DISPLAY "357 W " ST " " D-REC.
           MOVE "BBYQ2012" TO D-REC.
           WRITE D-REC.
           DISPLAY "358 W " ST " " D-REC.
           MOVE "3" TO S-KEY.
           START SFILE KEY >= S-KEY.
           DISPLAY "358S ST " SS " " S-REC.
           MOVE "BCXP1015" TO D-REC.
           REWRITE D-REC.
           DISPLAY "359 RW " ST " " D-REC.
           MOVE "@" TO D-K1.
           START DFILE KEY = D-K1.
           DISPLAY "360 ST " ST " " D-REC.
           MOVE "BA" TO D-KEY.
           READ DFILE.
           DISPLAY "361 RK " ST " " D-REC.
           MOVE "P1" TO D-UNQ.
           READ DFILE KEY IS D-UNQ.
           DISPLAY "362 RU " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "363 RN " ST " " D-REC.
           MOVE "X" TO D-GRP.
           READ DFILE KEY IS D-GRP.
           DISPLAY "364 RG " ST " " D-REC.
           MOVE "P2" TO D-UNQ.
           READ DFILE KEY IS D-UNQ.
           DISPLAY "365 RU " ST " " D-REC.
           MOVE "Q2" TO D-UNQ.
           START DFILE KEY > D-UNQ.
           DISPLAY "366 ST " ST " " D-REC.
           MOVE "CCYQ1937" TO D-REC.
           WRITE D-REC.
           DISPLAY "367 W " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "368 RN " ST " " D-REC.
           MOVE "X" TO D-GRP.
           READ DFILE KEY IS D-GRP.
           DISPLAY "369 RG " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "370 RN " ST " " D-REC.
           MOVE "BB" TO D-KEY.
           READ DFILE.
           DISPLAY "371 RK " ST " " D-REC.
           MOVE "D" TO D-K1.
           START DFILE KEY <= D-K1.
           DISPLAY "372 ST " ST " " D-REC.
           MOVE "CB" TO D-KEY.
           READ DFILE.
           DISPLAY "373 RK " ST " " D-REC.
           MOVE "CBYQ2893" TO D-REC.
           WRITE D-REC.
           DISPLAY "374 W " ST " " D-REC.
           MOVE "BA" TO D-KEY.
           READ DFILE.
           DISPLAY "375 RK " ST " " D-REC.
           MOVE "BA" TO D-KEY.
           START DFILE KEY > D-KEY.
           DISPLAY "376 ST " ST " " D-REC.
           MOVE "D" TO D-K1.
           START DFILE KEY = D-K1.
           DISPLAY "377 ST " ST " " D-REC.
           CLOSE SFILE.
           OPEN EXTEND SFILE.
           DISPLAY "377S OPEN " SS " " S-REC.
           MOVE "BAYQ1559" TO D-REC.
           REWRITE D-REC.
           DISPLAY "378 RW " ST " " D-REC.
           MOVE "CA" TO D-KEY.
           DELETE DFILE.
           DISPLAY "379 DEL " ST " " D-REC.
           MOVE "BBXP2947" TO D-REC.
           WRITE D-REC.
           DISPLAY "380 W " ST " " D-REC.
           MOVE "BAXP2039" TO D-REC.
           WRITE D-REC.
           DISPLAY "381 W " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "382 RN " ST " " D-REC.
           MOVE "BCYQ2856" TO D-REC.
           WRITE D-REC.
           DISPLAY "383 W " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "384 RN " ST " " D-REC.
           MOVE "CC" TO D-KEY.
           START DFILE KEY NOT > D-KEY.
           DISPLAY "385 ST " ST " " D-REC.
           MOVE "CBYP2365" TO D-REC.
           WRITE D-REC.
           DISPLAY "386 W " ST " " D-REC.
           MOVE "A" TO D-K1.
           START DFILE KEY < D-K1.
           DISPLAY "387 ST " ST " " D-REC.
           MOVE "X" TO D-GRP.
           READ DFILE KEY IS D-GRP.
           DISPLAY "388 RG " ST " " D-REC.
           MOVE "C@" TO D-KEY.
           START DFILE KEY NOT > D-KEY.
           DISPLAY "389 ST " ST " " D-REC.
           MOVE "AAYP1839" TO D-REC.
           REWRITE D-REC.
           DISPLAY "390 RW " ST " " D-REC.
           MOVE "X" TO D-GRP.
           START DFILE KEY = D-GRP.
           DISPLAY "391 ST " ST " " D-REC.
           MOVE "CAXQ1114" TO D-REC.
           REWRITE D-REC.
           DISPLAY "392 RW " ST " " D-REC.
           MOVE "CAYP1954" TO D-REC.
           WRITE D-REC.
           DISPLAY "393 W " ST " " D-REC.
           MOVE "CBXQ1034" TO D-REC.
           REWRITE D-REC.
           DISPLAY "394 RW " ST " " D-REC.
           MOVE "D" TO D-K1.
           START DFILE KEY = D-K1.
           DISPLAY "395 ST " ST " " D-REC.
           MOVE "CCXQ2663" TO D-REC.
           WRITE D-REC.
           DISPLAY "396 W " ST " " D-REC.
           MOVE "BBYR1491" TO D-REC.
           REWRITE D-REC.
           DISPLAY "397 RW " ST " " D-REC.
           MOVE "Y" TO D-GRP.
           START DFILE KEY <= D-GRP.
           DISPLAY "398 ST " ST " " D-REC.
           MOVE "AA" TO D-KEY.
           START DFILE KEY >= D-KEY.
           DISPLAY "399 ST " ST " " D-REC.
           READ DFILE PREVIOUS.
           DISPLAY "400 RP " ST " " D-REC.
           CLOSE SFILE.
           OPEN EXTEND SFILE.
           DISPLAY "400S OPEN " SS " " S-REC.
           CLOSE DFILE. OPEN I-O DFILE.
           DISPLAY "401 REOPEN " ST " " D-REC.
           READ DFILE PREVIOUS.
           DISPLAY "402 RP " ST " " D-REC.
           CLOSE DFILE. OPEN INPUT DFILE.
           READ DFILE NEXT.
           DISPLAY "403 IN " ST " " D-REC.
           MOVE "AAX" TO D-REC. WRITE D-REC.
           DISPLAY "403 INW " ST " " D-REC.
           CLOSE DFILE. OPEN I-O DFILE.
           MOVE "Q2" TO D-UNQ.
           START DFILE KEY >= D-UNQ.
           DISPLAY "404 ST " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "405 RN " ST " " D-REC.
           CLOSE SFILE.
           OPEN OUTPUT SFILE.
           DISPLAY "405S OPEN " SS " " S-REC.
           MOVE "BB" TO D-KEY.
           DELETE DFILE.
           DISPLAY "406 DEL " ST " " D-REC.
           MOVE "CC" TO D-KEY.
           START DFILE KEY NOT < D-KEY.
           DISPLAY "407 ST " ST " " D-REC.
           MOVE "2a" TO S-REC.
           WRITE S-REC.
           DISPLAY "407S W " SS " " S-REC.
           READ DFILE NEXT.
           DISPLAY "408 RN " ST " " D-REC.
           MOVE "R1" TO D-UNQ.
           READ DFILE KEY IS D-UNQ.
           DISPLAY "409 RU " ST " " D-REC.
           MOVE "CCXQ1308" TO D-REC.
           REWRITE D-REC.
           DISPLAY "410 RW " ST " " D-REC.
           MOVE "BC" TO D-KEY.
           READ DFILE.
           DISPLAY "411 RK " ST " " D-REC.
           READ SFILE.
           DISPLAY "411S R " SS " " S-REC.
           READ DFILE NEXT.
           DISPLAY "412 RN " ST " " D-REC.
           MOVE "5a" TO S-REC.
           WRITE S-REC.
           DISPLAY "412S W " SS " " S-REC.
           MOVE "B" TO D-K1.
           START DFILE KEY <= D-K1.
           DISPLAY "413 ST " ST " " D-REC.
           MOVE "2b" TO S-REC.
           WRITE S-REC.
           DISPLAY "413S W " SS " " S-REC.
           MOVE "A@" TO D-KEY.
           START DFILE KEY <= D-KEY.
           DISPLAY "414 ST " ST " " D-REC.
           MOVE "2y" TO S-REC.
           REWRITE S-REC.
           DISPLAY "414S RW " SS " " S-REC.
           MOVE "BAYP1891" TO D-REC.
           REWRITE D-REC.
           DISPLAY "415 RW " ST " " D-REC.
           READ SFILE.
           DISPLAY "415S R " SS " " S-REC.
           MOVE "AA" TO D-KEY.
           READ DFILE.
           DISPLAY "416 RK " ST " " D-REC.
           MOVE "CB" TO D-KEY.
           READ DFILE.
           DISPLAY "417 RK " ST " " D-REC.
           MOVE "BBXQ2348" TO D-REC.
           WRITE D-REC.
           DISPLAY "418 W " ST " " D-REC.
           READ SFILE.
           DISPLAY "418S R " SS " " S-REC.
           MOVE "B@" TO D-KEY.
           START DFILE KEY NOT > D-KEY.
           DISPLAY "419 ST " ST " " D-REC.
           MOVE "CB" TO D-KEY.
           DELETE DFILE.
           DISPLAY "420 DEL " ST " " D-REC.
           MOVE "B" TO D-K1.
           START DFILE KEY NOT > D-K1.
           DISPLAY "421 ST " ST " " D-REC.
           MOVE "A" TO D-GRP.
           START DFILE KEY <= D-GRP.
           DISPLAY "422 ST " ST " " D-REC.
           MOVE "6c" TO S-REC.
           WRITE S-REC.
           DISPLAY "422S W " SS " " S-REC.
           MOVE "CAXQ1999" TO D-REC.
           WRITE D-REC.
           DISPLAY "423 W " ST " " D-REC.
           READ DFILE PREVIOUS.
           DISPLAY "424 RP " ST " " D-REC.
           READ DFILE PREVIOUS.
           DISPLAY "425 RP " ST " " D-REC.
           MOVE "CBYR1595" TO D-REC.
           WRITE D-REC.
           DISPLAY "426 W " ST " " D-REC.
           MOVE "B@" TO D-KEY.
           START DFILE KEY NOT < D-KEY.
           DISPLAY "427 ST " ST " " D-REC.
           MOVE "Y" TO D-GRP.
           READ DFILE KEY IS D-GRP.
           DISPLAY "428 RG " ST " " D-REC.
           CLOSE DFILE. OPEN I-O DFILE.
           DISPLAY "429 REOPEN " ST " " D-REC.
           CLOSE SFILE.
           OPEN INPUT SFILE.
           DISPLAY "429S OPEN " SS " " S-REC.
           MOVE "CAYQ1190" TO D-REC.
           WRITE D-REC.
           DISPLAY "430 W " ST " " D-REC.
           READ DFILE PREVIOUS.
           DISPLAY "431 RP " ST " " D-REC.
           MOVE "BCYQ1148" TO D-REC.
           WRITE D-REC.
           DISPLAY "432 W " ST " " D-REC.
           MOVE "AB" TO D-KEY.
           DELETE DFILE.
           DISPLAY "433 DEL " ST " " D-REC.
           READ DFILE PREVIOUS.
           DISPLAY "434 RP " ST " " D-REC.
           MOVE "R1" TO D-UNQ.
           READ DFILE KEY IS D-UNQ.
           DISPLAY "435 RU " ST " " D-REC.
           MOVE "8a" TO S-REC.
           WRITE S-REC.
           DISPLAY "435S W " SS " " S-REC.
           MOVE "Z" TO D-GRP.
           START DFILE KEY < D-GRP.
           DISPLAY "436 ST " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "437 RN " ST " " D-REC.
           MOVE "AAYP1319" TO D-REC.
           WRITE D-REC.
           DISPLAY "438 W " ST " " D-REC.
           MOVE "6a" TO S-REC.
           WRITE S-REC.
           DISPLAY "438S W " SS " " S-REC.
           READ DFILE NEXT.
           DISPLAY "439 RN " ST " " D-REC.
           MOVE "BA" TO D-KEY.
           DELETE DFILE.
           DISPLAY "440 DEL " ST " " D-REC.
           READ DFILE PREVIOUS.
           DISPLAY "441 RP " ST " " D-REC.
           READ DFILE PREVIOUS.
           DISPLAY "442 RP " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "443 RN " ST " " D-REC.
           DELETE SFILE.
           DISPLAY "443S DEL " SS " " S-REC.
           MOVE "Q1" TO D-UNQ.
           READ DFILE KEY IS D-UNQ.
           DISPLAY "444 RU " ST " " D-REC.
           MOVE "BCYP2860" TO D-REC.
           WRITE D-REC.
           DISPLAY "445 W " ST " " D-REC.
           MOVE "4a" TO S-REC.
           WRITE S-REC.
           DISPLAY "445S W " SS " " S-REC.
           MOVE "BAYR1169" TO D-REC.
           WRITE D-REC.
           DISPLAY "446 W " ST " " D-REC.
           MOVE "Z" TO D-GRP.
           START DFILE KEY = D-GRP.
           DISPLAY "447 ST " ST " " D-REC.
           MOVE "@" TO D-K1.
           START DFILE KEY = D-K1.
           DISPLAY "448 ST " ST " " D-REC.
           CLOSE SFILE.
           OPEN INPUT SFILE.
           DISPLAY "448S OPEN " SS " " S-REC.
           MOVE "BBYQ1454" TO D-REC.
           REWRITE D-REC.
           DISPLAY "449 RW " ST " " D-REC.
           MOVE "9a" TO S-REC.
           WRITE S-REC.
           DISPLAY "449S W " SS " " S-REC.
           MOVE "ACXQ2921" TO D-REC.
           REWRITE D-REC.
           DISPLAY "450 RW " ST " " D-REC.
           CLOSE SFILE.
           OPEN I-O SFILE.
           DISPLAY "450S OPEN " SS " " S-REC.
           MOVE "AAYP1588" TO D-REC.
           WRITE D-REC.
           DISPLAY "451 W " ST " " D-REC.
           MOVE "BA" TO D-KEY.
           START DFILE KEY >= D-KEY.
           DISPLAY "452 ST " ST " " D-REC.
           MOVE "ABXP1567" TO D-REC.
           WRITE D-REC.
           DISPLAY "453 W " ST " " D-REC.
           READ DFILE PREVIOUS.
           DISPLAY "454 RP " ST " " D-REC.
           READ SFILE.
           DISPLAY "454S R " SS " " S-REC.
           READ DFILE NEXT.
           DISPLAY "455 RN " ST " " D-REC.
           MOVE "CCYQ2892" TO D-REC.
           WRITE D-REC.
           DISPLAY "456 W " ST " " D-REC.
           MOVE "2x" TO S-REC.
           REWRITE S-REC.
           DISPLAY "456S RW " SS " " S-REC.
           MOVE "Z" TO D-GRP.
           START DFILE KEY > D-GRP.
           DISPLAY "457 ST " ST " " D-REC.
           READ DFILE PREVIOUS.
           DISPLAY "458 RP " ST " " D-REC.
           MOVE "Y" TO D-GRP.
           READ DFILE KEY IS D-GRP.
           DISPLAY "459 RG " ST " " D-REC.
           MOVE "CB" TO D-KEY.
           READ DFILE.
           DISPLAY "460 RK " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "461 RN " ST " " D-REC.
           MOVE "CAXQ2512" TO D-REC.
           WRITE D-REC.
           DISPLAY "462 W " ST " " D-REC.
           MOVE "CCYQ1183" TO D-REC.
           REWRITE D-REC.
           DISPLAY "463 RW " ST " " D-REC.
           MOVE "ACXR1203" TO D-REC.
           WRITE D-REC.
           DISPLAY "464 W " ST " " D-REC.
           MOVE "CAYR1156" TO D-REC.
           WRITE D-REC.
           DISPLAY "465 W " ST " " D-REC.
           MOVE "AB" TO D-KEY.
           READ DFILE.
           DISPLAY "466 RK " ST " " D-REC.
           READ DFILE PREVIOUS.
           DISPLAY "467 RP " ST " " D-REC.
           READ SFILE.
           DISPLAY "467S R " SS " " S-REC.
           CLOSE DFILE. OPEN I-O DFILE.
           DISPLAY "468 REOPEN " ST " " D-REC.
           MOVE "P1" TO D-UNQ.
           START DFILE KEY NOT < D-UNQ.
           DISPLAY "469 ST " ST " " D-REC.
           MOVE "3c" TO S-REC.
           WRITE S-REC.
           DISPLAY "469S W " SS " " S-REC.
           READ DFILE PREVIOUS.
           DISPLAY "470 RP " ST " " D-REC.
           MOVE "ACYP2814" TO D-REC.
           WRITE D-REC.
           DISPLAY "471 W " ST " " D-REC.
           MOVE "Y" TO D-GRP.
           START DFILE KEY NOT > D-GRP.
           DISPLAY "472 ST " ST " " D-REC.
           READ DFILE PREVIOUS.
           DISPLAY "473 RP " ST " " D-REC.
           READ DFILE PREVIOUS.
           DISPLAY "474 RP " ST " " D-REC.
           MOVE "5b" TO S-REC.
           WRITE S-REC.
           DISPLAY "474S W " SS " " S-REC.
           MOVE "BCYP2874" TO D-REC.
           WRITE D-REC.
           DISPLAY "475 W " ST " " D-REC.
           MOVE "AAXP1661" TO D-REC.
           WRITE D-REC.
           DISPLAY "476 W " ST " " D-REC.
           READ DFILE PREVIOUS.
           DISPLAY "477 RP " ST " " D-REC.
           MOVE "AA" TO D-KEY.
           DELETE DFILE.
           DISPLAY "478 DEL " ST " " D-REC.
           MOVE "CBXP1522" TO D-REC.
           WRITE D-REC.
           DISPLAY "479 W " ST " " D-REC.
           MOVE "D" TO D-K1.
           START DFILE KEY < D-K1.
           DISPLAY "480 ST " ST " " D-REC.
           MOVE "@" TO D-K1.
           START DFILE KEY = D-K1.
           DISPLAY "481 ST " ST " " D-REC.
           MOVE "CAYP1725" TO D-REC.
           REWRITE D-REC.
           DISPLAY "482 RW " ST " " D-REC.
           MOVE "4x" TO S-REC.
           REWRITE S-REC.
           DISPLAY "482S RW " SS " " S-REC.
           MOVE "BBYP1533" TO D-REC.
           WRITE D-REC.
           DISPLAY "483 W " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "484 RN " ST " " D-REC.
           READ DFILE PREVIOUS.
           DISPLAY "485 RP " ST " " D-REC.
           MOVE "BC" TO D-KEY.
           START DFILE KEY NOT < D-KEY.
           DISPLAY "486 ST " ST " " D-REC.
           MOVE "CCYQ2761" TO D-REC.
           WRITE D-REC.
           DISPLAY "487 W " ST " " D-REC.
           READ DFILE NEXT.
           DISPLAY "488 RN " ST " " D-REC.
           MOVE "CC" TO D-KEY.
           READ DFILE.
           DISPLAY "489 RK " ST " " D-REC.
           MOVE "Y" TO D-GRP.
           READ DFILE KEY IS D-GRP.
           DISPLAY "490 RG " ST " " D-REC.
           READ SFILE.
           DISPLAY "490S R " SS " " S-REC.
           MOVE "Q1" TO D-UNQ.
           START DFILE KEY = D-UNQ.
           DISPLAY "491 ST " ST " " D-REC.
           DELETE SFILE.
           DISPLAY "491S DEL " SS " " S-REC.
           READ DFILE PREVIOUS.
           DISPLAY "492 RP " ST " " D-REC.
           MOVE "BBXP2022" TO D-REC.
           REWRITE D-REC.
           DISPLAY "493 RW " ST " " D-REC.
           MOVE "A" TO D-GRP.
           START DFILE KEY NOT < D-GRP.
           DISPLAY "494 ST " ST " " D-REC.
           MOVE "7" TO S-KEY.
           START SFILE KEY >= S-KEY.
           DISPLAY "494S ST " SS " " S-REC.
           MOVE "Y" TO D-GRP.
           START DFILE KEY >= D-GRP.
           DISPLAY "495 ST " ST " " D-REC.
           MOVE "X" TO D-GRP.
           READ DFILE KEY IS D-GRP.
           DISPLAY "496 RG " ST " " D-REC.
           READ DFILE PREVIOUS.
           DISPLAY "497 RP " ST " " D-REC.
           MOVE "BCXR1174" TO D-REC.
           WRITE D-REC.
           DISPLAY "498 W " ST " " D-REC.
           READ DFILE PREVIOUS.
           DISPLAY "499 RP " ST " " D-REC.
           CLOSE DFILE.
           CLOSE SFILE.
           STOP RUN.
