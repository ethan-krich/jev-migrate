       IDENTIFICATION DIVISION.
       PROGRAM-ID. RELDENSE.
       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT RELF ASSIGN TO "drel.dat"
               ORGANIZATION IS RELATIVE
               ACCESS MODE IS DYNAMIC
               RELATIVE KEY IS RK
               FILE STATUS IS ST.
           SELECT QFILE ASSIGN TO "drel.dat"
               ORGANIZATION IS RELATIVE
               ACCESS MODE IS SEQUENTIAL
               RELATIVE KEY IS QK
               FILE STATUS IS QS.
       DATA DIVISION.
       FILE SECTION.
       FD RELF.
       01 R-REC PIC X(4).
       FD QFILE.
       01 Q-REC PIC X(4).
       WORKING-STORAGE SECTION.
       01 ST PIC XX.
       01 QS PIC XX.
       01 RK PIC 99.
       01 QK PIC 9.
       PROCEDURE DIVISION.
           OPEN OUTPUT RELF. CLOSE RELF.
           OPEN I-O RELF. DISPLAY "open " ST.
           MOVE 3 TO RK.
           DELETE RELF.
           DISPLAY "000 DEL " ST " " RK " " R-REC.
           MOVE 7 TO RK.
           READ RELF.
           DISPLAY "001 RK " ST " " RK " " R-REC.
           MOVE 8 TO RK.
           START RELF KEY NOT < RK.
           DISPLAY "002 ST " ST " " RK " " R-REC.
           MOVE 5 TO RK.
           DELETE RELF.
           DISPLAY "003 DEL " ST " " RK " " R-REC.
           MOVE 3 TO RK.
           DELETE RELF.
           DISPLAY "004 DEL " ST " " RK " " R-REC.
           MOVE 7 TO RK.
           MOVE "w005" TO R-REC.
           WRITE R-REC.
           DISPLAY "005 W " ST " " RK " " R-REC.
           MOVE 0 TO RK.
           MOVE "w006" TO R-REC.
           WRITE R-REC.
           DISPLAY "006 W " ST " " RK " " R-REC.
           MOVE 10 TO RK.
           MOVE "w007" TO R-REC.
           WRITE R-REC.
           DISPLAY "007 W " ST " " RK " " R-REC.
           MOVE 4 TO RK.
           DELETE RELF.
           DISPLAY "008 DEL " ST " " RK " " R-REC.
           MOVE 8 TO RK.
           MOVE "w009" TO R-REC.
           WRITE R-REC.
           DISPLAY "009 W " ST " " RK " " R-REC.
           MOVE 11 TO RK.
           MOVE "r010" TO R-REC.
           REWRITE R-REC.
           DISPLAY "010 RW " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "011 RN " ST " " RK " " R-REC.
           READ RELF PREVIOUS.
           DISPLAY "012 RP " ST " " RK " " R-REC.
           MOVE 4 TO RK.
           DELETE RELF.
           DISPLAY "013 DEL " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "014 RN " ST " " RK " " R-REC.
           READ RELF PREVIOUS.
           DISPLAY "015 RP " ST " " RK " " R-REC.
           CLOSE RELF.
           OPEN I-O QFILE.
           DISPLAY "016 QOPEN " QS.
           MOVE 2 TO QK.
           START QFILE KEY >= QK.
           DISPLAY "017 ST Q" QS " " QK " " Q-REC.
           READ QFILE.
           DISPLAY "018 R Q" QS " " QK " " Q-REC.
           CLOSE QFILE. OPEN I-O RELF.
           DISPLAY "019 BACK Q" QS " " QK " " Q-REC.
           MOVE 3 TO RK.
           START RELF KEY <= RK.
           DISPLAY "020 ST " ST " " RK " " R-REC.
           MOVE 11 TO RK.
           READ RELF.
           DISPLAY "021 RK " ST " " RK " " R-REC.
           MOVE 2 TO RK.
           MOVE "w022" TO R-REC.
           WRITE R-REC.
           DISPLAY "022 W " ST " " RK " " R-REC.
           MOVE 8 TO RK.
           DELETE RELF.
           DISPLAY "023 DEL " ST " " RK " " R-REC.
           MOVE 10 TO RK.
           MOVE "r024" TO R-REC.
           REWRITE R-REC.
           DISPLAY "024 RW " ST " " RK " " R-REC.
           MOVE 11 TO RK.
           MOVE "w025" TO R-REC.
           WRITE R-REC.
           DISPLAY "025 W " ST " " RK " " R-REC.
           MOVE 8 TO RK.
           START RELF KEY NOT > RK.
           DISPLAY "026 ST " ST " " RK " " R-REC.
           MOVE 5 TO RK.
           MOVE "w027" TO R-REC.
           WRITE R-REC.
           DISPLAY "027 W " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "028 RN " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "029 RN " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "030 RN " ST " " RK " " R-REC.
           MOVE 2 TO RK.
           START RELF KEY <= RK.
           DISPLAY "031 ST " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "032 RN " ST " " RK " " R-REC.
           MOVE 4 TO RK.
           MOVE "w033" TO R-REC.
           WRITE R-REC.
           DISPLAY "033 W " ST " " RK " " R-REC.
           MOVE 0 TO RK.
           START RELF KEY NOT < RK.
           DISPLAY "034 ST " ST " " RK " " R-REC.
           CLOSE RELF. OPEN I-O RELF.
           DISPLAY "035 REOPEN " ST " " RK " " R-REC.
           MOVE 8 TO RK.
           READ RELF.
           DISPLAY "036 RK " ST " " RK " " R-REC.
           MOVE 9 TO RK.
           START RELF KEY <= RK.
           DISPLAY "037 ST " ST " " RK " " R-REC.
           MOVE 2 TO RK.
           READ RELF.
           DISPLAY "038 RK " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "039 RN " ST " " RK " " R-REC.
           READ RELF PREVIOUS.
           DISPLAY "040 RP " ST " " RK " " R-REC.
           MOVE 9 TO RK.
           MOVE "w041" TO R-REC.
           WRITE R-REC.
           DISPLAY "041 W " ST " " RK " " R-REC.
           MOVE 9 TO RK.
           START RELF KEY NOT < RK.
           DISPLAY "042 ST " ST " " RK " " R-REC.
           MOVE 3 TO RK.
           START RELF KEY <= RK.
           DISPLAY "043 ST " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "044 RN " ST " " RK " " R-REC.
           MOVE 10 TO RK.
           START RELF KEY <= RK.
           DISPLAY "045 ST " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "046 RN " ST " " RK " " R-REC.
           CLOSE RELF.
           OPEN INPUT QFILE.
           DISPLAY "047 QOPEN " QS.
           READ QFILE.
           DISPLAY "048 R Q" QS " " QK " " Q-REC.
           MOVE 1 TO QK.
           START QFILE KEY >= QK.
           DISPLAY "049 ST Q" QS " " QK " " Q-REC.
           MOVE "q050" TO Q-REC.
           WRITE Q-REC.
           DISPLAY "050 W Q" QS " " QK " " Q-REC.
           MOVE "q051" TO Q-REC.
           REWRITE Q-REC.
           DISPLAY "051 RW Q" QS " " QK " " Q-REC.
           CLOSE QFILE. OPEN I-O RELF.
           DISPLAY "052 BACK Q" QS " " QK " " Q-REC.
           CLOSE RELF.
           OPEN I-O QFILE.
           DISPLAY "053 QOPEN " QS.
           READ QFILE.
           DISPLAY "054 R Q" QS " " QK " " Q-REC.
           READ QFILE.
           DISPLAY "055 R Q" QS " " QK " " Q-REC.
           READ QFILE.
           DISPLAY "056 R Q" QS " " QK " " Q-REC.
           MOVE 1 TO QK.
           START QFILE KEY <= QK.
           DISPLAY "057 ST Q" QS " " QK " " Q-REC.
           CLOSE QFILE. OPEN I-O RELF.
           DISPLAY "058 BACK Q" QS " " QK " " Q-REC.
           READ RELF NEXT.
           DISPLAY "059 RN " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "060 RN " ST " " RK " " R-REC.
           MOVE 0 TO RK.
           MOVE "r061" TO R-REC.
           REWRITE R-REC.
           DISPLAY "061 RW " ST " " RK " " R-REC.
           MOVE 5 TO RK.
           READ RELF.
           DISPLAY "062 RK " ST " " RK " " R-REC.
           MOVE 1 TO RK.
           MOVE "w063" TO R-REC.
           WRITE R-REC.
           DISPLAY "063 W " ST " " RK " " R-REC.
           MOVE 11 TO RK.
           READ RELF.
           DISPLAY "064 RK " ST " " RK " " R-REC.
           MOVE 0 TO RK.
           MOVE "r065" TO R-REC.
           REWRITE R-REC.
           DISPLAY "065 RW " ST " " RK " " R-REC.
           CLOSE RELF. OPEN I-O RELF.
           DISPLAY "066 REOPEN " ST " " RK " " R-REC.
           MOVE 6 TO RK.
           MOVE "w067" TO R-REC.
           WRITE R-REC.
           DISPLAY "067 W " ST " " RK " " R-REC.
           MOVE 4 TO RK.
           MOVE "w068" TO R-REC.
           WRITE R-REC.
           DISPLAY "068 W " ST " " RK " " R-REC.
           MOVE 2 TO RK.
           DELETE RELF.
           DISPLAY "069 DEL " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "070 RN " ST " " RK " " R-REC.
           MOVE 11 TO RK.
           START RELF KEY = RK.
           DISPLAY "071 ST " ST " " RK " " R-REC.
           READ RELF PREVIOUS.
           DISPLAY "072 RP " ST " " RK " " R-REC.
           MOVE 7 TO RK.
           MOVE "r073" TO R-REC.
           REWRITE R-REC.
           DISPLAY "073 RW " ST " " RK " " R-REC.
           MOVE 6 TO RK.
           MOVE "w074" TO R-REC.
           WRITE R-REC.
           DISPLAY "074 W " ST " " RK " " R-REC.
           MOVE 11 TO RK.
           READ RELF.
           DISPLAY "075 RK " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "076 RN " ST " " RK " " R-REC.
           MOVE 8 TO RK.
           START RELF KEY > RK.
           DISPLAY "077 ST " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "078 RN " ST " " RK " " R-REC.
           CLOSE RELF. OPEN I-O RELF.
           DISPLAY "079 REOPEN " ST " " RK " " R-REC.
           READ RELF PREVIOUS.
           DISPLAY "080 RP " ST " " RK " " R-REC.
           MOVE 8 TO RK.
           MOVE "r081" TO R-REC.
           REWRITE R-REC.
           DISPLAY "081 RW " ST " " RK " " R-REC.
           READ RELF PREVIOUS.
           DISPLAY "082 RP " ST " " RK " " R-REC.
           MOVE 11 TO RK.
           DELETE RELF.
           DISPLAY "083 DEL " ST " " RK " " R-REC.
           MOVE 9 TO RK.
           READ RELF.
           DISPLAY "084 RK " ST " " RK " " R-REC.
           MOVE 8 TO RK.
           MOVE "r085" TO R-REC.
           REWRITE R-REC.
           DISPLAY "085 RW " ST " " RK " " R-REC.
           MOVE 3 TO RK.
           MOVE "r086" TO R-REC.
           REWRITE R-REC.
           DISPLAY "086 RW " ST " " RK " " R-REC.
           MOVE 9 TO RK.
           START RELF KEY >= RK.
           DISPLAY "087 ST " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "088 RN " ST " " RK " " R-REC.
           CLOSE RELF. OPEN I-O RELF.
           DISPLAY "089 REOPEN " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "090 RN " ST " " RK " " R-REC.
           MOVE 10 TO RK.
           MOVE "w091" TO R-REC.
           WRITE R-REC.
           DISPLAY "091 W " ST " " RK " " R-REC.
           MOVE 11 TO RK.
           DELETE RELF.
           DISPLAY "092 DEL " ST " " RK " " R-REC.
           MOVE 7 TO RK.
           START RELF KEY >= RK.
           DISPLAY "093 ST " ST " " RK " " R-REC.
           READ RELF PREVIOUS.
           DISPLAY "094 RP " ST " " RK " " R-REC.
           READ RELF PREVIOUS.
           DISPLAY "095 RP " ST " " RK " " R-REC.
           MOVE 6 TO RK.
           READ RELF.
           DISPLAY "096 RK " ST " " RK " " R-REC.
           MOVE 1 TO RK.
           START RELF KEY <= RK.
           DISPLAY "097 ST " ST " " RK " " R-REC.
           MOVE 6 TO RK.
           MOVE "w098" TO R-REC.
           WRITE R-REC.
           DISPLAY "098 W " ST " " RK " " R-REC.
           MOVE 10 TO RK.
           MOVE "r099" TO R-REC.
           REWRITE R-REC.
           DISPLAY "099 RW " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "100 RN " ST " " RK " " R-REC.
           MOVE 10 TO RK.
           START RELF KEY <= RK.
           DISPLAY "101 ST " ST " " RK " " R-REC.
           MOVE 4 TO RK.
           MOVE "w102" TO R-REC.
           WRITE R-REC.
           DISPLAY "102 W " ST " " RK " " R-REC.
           MOVE 8 TO RK.
           MOVE "w103" TO R-REC.
           WRITE R-REC.
           DISPLAY "103 W " ST " " RK " " R-REC.
           MOVE 11 TO RK.
           READ RELF.
           DISPLAY "104 RK " ST " " RK " " R-REC.
           MOVE 10 TO RK.
           READ RELF.
           DISPLAY "105 RK " ST " " RK " " R-REC.
           MOVE 5 TO RK.
           MOVE "w106" TO R-REC.
           WRITE R-REC.
           DISPLAY "106 W " ST " " RK " " R-REC.
           MOVE 3 TO RK.
           MOVE "w107" TO R-REC.
           WRITE R-REC.
           DISPLAY "107 W " ST " " RK " " R-REC.
           MOVE 11 TO RK.
           READ RELF.
           DISPLAY "108 RK " ST " " RK " " R-REC.
           MOVE 7 TO RK.
           MOVE "w109" TO R-REC.
           WRITE R-REC.
           DISPLAY "109 W " ST " " RK " " R-REC.
           MOVE 3 TO RK.
           DELETE RELF.
           DISPLAY "110 DEL " ST " " RK " " R-REC.
           MOVE 9 TO RK.
           MOVE "w111" TO R-REC.
           WRITE R-REC.
           DISPLAY "111 W " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "112 RN " ST " " RK " " R-REC.
           CLOSE RELF.
           OPEN EXTEND QFILE.
           DISPLAY "113 QOPEN " QS.
           READ QFILE.
           DISPLAY "114 R Q" QS " " QK " " Q-REC.
           CLOSE QFILE. OPEN I-O RELF.
           DISPLAY "115 BACK Q" QS " " QK " " Q-REC.
           MOVE 11 TO RK.
           START RELF KEY = RK.
           DISPLAY "116 ST " ST " " RK " " R-REC.
           MOVE 5 TO RK.
           MOVE "w117" TO R-REC.
           WRITE R-REC.
           DISPLAY "117 W " ST " " RK " " R-REC.
           MOVE 2 TO RK.
           READ RELF.
           DISPLAY "118 RK " ST " " RK " " R-REC.
           CLOSE RELF.
           OPEN I-O QFILE.
           DISPLAY "119 QOPEN " QS.
           READ QFILE.
           DISPLAY "120 R Q" QS " " QK " " Q-REC.
           DELETE QFILE.
           DISPLAY "121 DEL Q" QS " " QK " " Q-REC.
           DELETE QFILE.
           DISPLAY "122 DEL Q" QS " " QK " " Q-REC.
           DELETE QFILE.
           DISPLAY "123 DEL Q" QS " " QK " " Q-REC.
           READ QFILE.
           DISPLAY "124 R Q" QS " " QK " " Q-REC.
           MOVE 5 TO QK.
           START QFILE KEY > QK.
           DISPLAY "125 ST Q" QS " " QK " " Q-REC.
           MOVE 11 TO QK.
           START QFILE KEY <= QK.
           DISPLAY "126 ST Q" QS " " QK " " Q-REC.
           CLOSE QFILE. OPEN I-O RELF.
           DISPLAY "127 BACK Q" QS " " QK " " Q-REC.
           MOVE 11 TO RK.
           START RELF KEY < RK.
           DISPLAY "128 ST " ST " " RK " " R-REC.
           CLOSE RELF. OPEN I-O RELF.
           DISPLAY "129 REOPEN " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "130 RN " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "131 RN " ST " " RK " " R-REC.
           MOVE 5 TO RK.
           MOVE "w132" TO R-REC.
           WRITE R-REC.
           DISPLAY "132 W " ST " " RK " " R-REC.
           READ RELF PREVIOUS.
           DISPLAY "133 RP " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "134 RN " ST " " RK " " R-REC.
           READ RELF PREVIOUS.
           DISPLAY "135 RP " ST " " RK " " R-REC.
           MOVE 4 TO RK.
           MOVE "w136" TO R-REC.
           WRITE R-REC.
           DISPLAY "136 W " ST " " RK " " R-REC.
           MOVE 1 TO RK.
           MOVE "w137" TO R-REC.
           WRITE R-REC.
           DISPLAY "137 W " ST " " RK " " R-REC.
           MOVE 2 TO RK.
           READ RELF.
           DISPLAY "138 RK " ST " " RK " " R-REC.
           MOVE 11 TO RK.
           READ RELF.
           DISPLAY "139 RK " ST " " RK " " R-REC.
           MOVE 10 TO RK.
           DELETE RELF.
           DISPLAY "140 DEL " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "141 RN " ST " " RK " " R-REC.
           MOVE 4 TO RK.
           START RELF KEY <= RK.
           DISPLAY "142 ST " ST " " RK " " R-REC.
           MOVE 5 TO RK.
           MOVE "w143" TO R-REC.
           WRITE R-REC.
           DISPLAY "143 W " ST " " RK " " R-REC.
           MOVE 4 TO RK.
           MOVE "w144" TO R-REC.
           WRITE R-REC.
           DISPLAY "144 W " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "145 RN " ST " " RK " " R-REC.
           MOVE 2 TO RK.
           DELETE RELF.
           DISPLAY "146 DEL " ST " " RK " " R-REC.
           MOVE 1 TO RK.
           START RELF KEY < RK.
           DISPLAY "147 ST " ST " " RK " " R-REC.
           READ RELF PREVIOUS.
           DISPLAY "148 RP " ST " " RK " " R-REC.
           MOVE 7 TO RK.
           START RELF KEY <= RK.
           DISPLAY "149 ST " ST " " RK " " R-REC.
           MOVE 4 TO RK.
           DELETE RELF.
           DISPLAY "150 DEL " ST " " RK " " R-REC.
           READ RELF PREVIOUS.
           DISPLAY "151 RP " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "152 RN " ST " " RK " " R-REC.
           MOVE 3 TO RK.
           READ RELF.
           DISPLAY "153 RK " ST " " RK " " R-REC.
           MOVE 3 TO RK.
           MOVE "w154" TO R-REC.
           WRITE R-REC.
           DISPLAY "154 W " ST " " RK " " R-REC.
           MOVE 1 TO RK.
           MOVE "r155" TO R-REC.
           REWRITE R-REC.
           DISPLAY "155 RW " ST " " RK " " R-REC.
           MOVE 0 TO RK.
           MOVE "w156" TO R-REC.
           WRITE R-REC.
           DISPLAY "156 W " ST " " RK " " R-REC.
           MOVE 2 TO RK.
           READ RELF.
           DISPLAY "157 RK " ST " " RK " " R-REC.
           MOVE 4 TO RK.
           MOVE "w158" TO R-REC.
           WRITE R-REC.
           DISPLAY "158 W " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "159 RN " ST " " RK " " R-REC.
           READ RELF PREVIOUS.
           DISPLAY "160 RP " ST " " RK " " R-REC.
           MOVE 3 TO RK.
           MOVE "r161" TO R-REC.
           REWRITE R-REC.
           DISPLAY "161 RW " ST " " RK " " R-REC.
           MOVE 5 TO RK.
           MOVE "w162" TO R-REC.
           WRITE R-REC.
           DISPLAY "162 W " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "163 RN " ST " " RK " " R-REC.
           MOVE 1 TO RK.
           READ RELF.
           DISPLAY "164 RK " ST " " RK " " R-REC.
           MOVE 6 TO RK.
           MOVE "r165" TO R-REC.
           REWRITE R-REC.
           DISPLAY "165 RW " ST " " RK " " R-REC.
           MOVE 9 TO RK.
           START RELF KEY <= RK.
           DISPLAY "166 ST " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "167 RN " ST " " RK " " R-REC.
           MOVE 9 TO RK.
           MOVE "w168" TO R-REC.
           WRITE R-REC.
           DISPLAY "168 W " ST " " RK " " R-REC.
           MOVE 5 TO RK.
           MOVE "w169" TO R-REC.
           WRITE R-REC.
           DISPLAY "169 W " ST " " RK " " R-REC.
           MOVE 6 TO RK.
           MOVE "r170" TO R-REC.
           REWRITE R-REC.
           DISPLAY "170 RW " ST " " RK " " R-REC.
           MOVE 6 TO RK.
           START RELF KEY >= RK.
           DISPLAY "171 ST " ST " " RK " " R-REC.
           READ RELF PREVIOUS.
           DISPLAY "172 RP " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "173 RN " ST " " RK " " R-REC.
           MOVE 8 TO RK.
           READ RELF.
           DISPLAY "174 RK " ST " " RK " " R-REC.
           READ RELF PREVIOUS.
           DISPLAY "175 RP " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "176 RN " ST " " RK " " R-REC.
           CLOSE RELF. OPEN I-O RELF.
           DISPLAY "177 REOPEN " ST " " RK " " R-REC.
           MOVE 5 TO RK.
           MOVE "w178" TO R-REC.
           WRITE R-REC.
           DISPLAY "178 W " ST " " RK " " R-REC.
           MOVE 2 TO RK.
           START RELF KEY >= RK.
           DISPLAY "179 ST " ST " " RK " " R-REC.
           MOVE 8 TO RK.
           START RELF KEY > RK.
           DISPLAY "180 ST " ST " " RK " " R-REC.
           MOVE 8 TO RK.
           MOVE "w181" TO R-REC.
           WRITE R-REC.
           DISPLAY "181 W " ST " " RK " " R-REC.
           MOVE 4 TO RK.
           MOVE "r182" TO R-REC.
           REWRITE R-REC.
           DISPLAY "182 RW " ST " " RK " " R-REC.
           MOVE 11 TO RK.
           MOVE "w183" TO R-REC.
           WRITE R-REC.
           DISPLAY "183 W " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "184 RN " ST " " RK " " R-REC.
           MOVE 2 TO RK.
           DELETE RELF.
           DISPLAY "185 DEL " ST " " RK " " R-REC.
           MOVE 10 TO RK.
           MOVE "w186" TO R-REC.
           WRITE R-REC.
           DISPLAY "186 W " ST " " RK " " R-REC.
           MOVE 9 TO RK.
           MOVE "w187" TO R-REC.
           WRITE R-REC.
           DISPLAY "187 W " ST " " RK " " R-REC.
           READ RELF PREVIOUS.
           DISPLAY "188 RP " ST " " RK " " R-REC.
           MOVE 3 TO RK.
           READ RELF.
           DISPLAY "189 RK " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "190 RN " ST " " RK " " R-REC.
           MOVE 10 TO RK.
           MOVE "w191" TO R-REC.
           WRITE R-REC.
           DISPLAY "191 W " ST " " RK " " R-REC.
           CLOSE RELF. OPEN I-O RELF.
           DISPLAY "192 REOPEN " ST " " RK " " R-REC.
           MOVE 8 TO RK.
           READ RELF.
           DISPLAY "193 RK " ST " " RK " " R-REC.
           READ RELF PREVIOUS.
           DISPLAY "194 RP " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "195 RN " ST " " RK " " R-REC.
           MOVE 10 TO RK.
           START RELF KEY <= RK.
           DISPLAY "196 ST " ST " " RK " " R-REC.
           MOVE 5 TO RK.
           MOVE "w197" TO R-REC.
           WRITE R-REC.
           DISPLAY "197 W " ST " " RK " " R-REC.
           READ RELF PREVIOUS.
           DISPLAY "198 RP " ST " " RK " " R-REC.
           MOVE 7 TO RK.
           MOVE "w199" TO R-REC.
           WRITE R-REC.
           DISPLAY "199 W " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "200 RN " ST " " RK " " R-REC.
           MOVE 0 TO RK.
           MOVE "w201" TO R-REC.
           WRITE R-REC.
           DISPLAY "201 W " ST " " RK " " R-REC.
           MOVE 6 TO RK.
           DELETE RELF.
           DISPLAY "202 DEL " ST " " RK " " R-REC.
           MOVE 7 TO RK.
           START RELF KEY > RK.
           DISPLAY "203 ST " ST " " RK " " R-REC.
           MOVE 5 TO RK.
           DELETE RELF.
           DISPLAY "204 DEL " ST " " RK " " R-REC.
           MOVE 4 TO RK.
           MOVE "w205" TO R-REC.
           WRITE R-REC.
           DISPLAY "205 W " ST " " RK " " R-REC.
           MOVE 3 TO RK.
           DELETE RELF.
           DISPLAY "206 DEL " ST " " RK " " R-REC.
           MOVE 11 TO RK.
           MOVE "w207" TO R-REC.
           WRITE R-REC.
           DISPLAY "207 W " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "208 RN " ST " " RK " " R-REC.
           MOVE 9 TO RK.
           START RELF KEY = RK.
           DISPLAY "209 ST " ST " " RK " " R-REC.
           READ RELF PREVIOUS.
           DISPLAY "210 RP " ST " " RK " " R-REC.
           MOVE 1 TO RK.
           MOVE "w211" TO R-REC.
           WRITE R-REC.
           DISPLAY "211 W " ST " " RK " " R-REC.
           MOVE 10 TO RK.
           MOVE "w212" TO R-REC.
           WRITE R-REC.
           DISPLAY "212 W " ST " " RK " " R-REC.
           MOVE 5 TO RK.
           START RELF KEY < RK.
           DISPLAY "213 ST " ST " " RK " " R-REC.
           MOVE 9 TO RK.
           MOVE "w214" TO R-REC.
           WRITE R-REC.
           DISPLAY "214 W " ST " " RK " " R-REC.
           MOVE 9 TO RK.
           DELETE RELF.
           DISPLAY "215 DEL " ST " " RK " " R-REC.
           MOVE 2 TO RK.
           MOVE "r216" TO R-REC.
           REWRITE R-REC.
           DISPLAY "216 RW " ST " " RK " " R-REC.
           CLOSE RELF.
           OPEN I-O QFILE.
           DISPLAY "217 QOPEN " QS.
           DELETE QFILE.
           DISPLAY "218 DEL Q" QS " " QK " " Q-REC.
           READ QFILE.
           DISPLAY "219 R Q" QS " " QK " " Q-REC.
           READ QFILE.
           DISPLAY "220 R Q" QS " " QK " " Q-REC.
           MOVE 9 TO QK.
           START QFILE KEY = QK.
           DISPLAY "221 ST Q" QS " " QK " " Q-REC.
           READ QFILE.
           DISPLAY "222 R Q" QS " " QK " " Q-REC.
           CLOSE QFILE. OPEN I-O RELF.
           DISPLAY "223 BACK Q" QS " " QK " " Q-REC.
           MOVE 2 TO RK.
           DELETE RELF.
           DISPLAY "224 DEL " ST " " RK " " R-REC.
           MOVE 8 TO RK.
           START RELF KEY > RK.
           DISPLAY "225 ST " ST " " RK " " R-REC.
           MOVE 4 TO RK.
           MOVE "w226" TO R-REC.
           WRITE R-REC.
           DISPLAY "226 W " ST " " RK " " R-REC.
           MOVE 2 TO RK.
           START RELF KEY NOT > RK.
           DISPLAY "227 ST " ST " " RK " " R-REC.
           MOVE 11 TO RK.
           MOVE "r228" TO R-REC.
           REWRITE R-REC.
           DISPLAY "228 RW " ST " " RK " " R-REC.
           MOVE 10 TO RK.
           DELETE RELF.
           DISPLAY "229 DEL " ST " " RK " " R-REC.
           MOVE 1 TO RK.
           READ RELF.
           DISPLAY "230 RK " ST " " RK " " R-REC.
           MOVE 2 TO RK.
           READ RELF.
           DISPLAY "231 RK " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "232 RN " ST " " RK " " R-REC.
           MOVE 9 TO RK.
           START RELF KEY > RK.
           DISPLAY "233 ST " ST " " RK " " R-REC.
           MOVE 4 TO RK.
           MOVE "w234" TO R-REC.
           WRITE R-REC.
           DISPLAY "234 W " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "235 RN " ST " " RK " " R-REC.
           CLOSE RELF.
           OPEN INPUT QFILE.
           DISPLAY "236 QOPEN " QS.
           READ QFILE.
           DISPLAY "237 R Q" QS " " QK " " Q-REC.
           MOVE 7 TO QK.
           START QFILE KEY >= QK.
           DISPLAY "238 ST Q" QS " " QK " " Q-REC.
           MOVE "q239" TO Q-REC.
           WRITE Q-REC.
           DISPLAY "239 W Q" QS " " QK " " Q-REC.
           MOVE 7 TO QK.
           START QFILE KEY < QK.
           DISPLAY "240 ST Q" QS " " QK " " Q-REC.
           MOVE "q241" TO Q-REC.
           WRITE Q-REC.
           DISPLAY "241 W Q" QS " " QK " " Q-REC.
           READ QFILE.
           DISPLAY "242 R Q" QS " " QK " " Q-REC.
           READ QFILE.
           DISPLAY "243 R Q" QS " " QK " " Q-REC.
           MOVE 7 TO QK.
           START QFILE KEY = QK.
           DISPLAY "244 ST Q" QS " " QK " " Q-REC.
           CLOSE QFILE. OPEN I-O RELF.
           DISPLAY "245 BACK Q" QS " " QK " " Q-REC.
           READ RELF NEXT.
           DISPLAY "246 RN " ST " " RK " " R-REC.
           MOVE 4 TO RK.
           MOVE "r247" TO R-REC.
           REWRITE R-REC.
           DISPLAY "247 RW " ST " " RK " " R-REC.
           MOVE 3 TO RK.
           MOVE "r248" TO R-REC.
           REWRITE R-REC.
           DISPLAY "248 RW " ST " " RK " " R-REC.
           MOVE 6 TO RK.
           START RELF KEY NOT > RK.
           DISPLAY "249 ST " ST " " RK " " R-REC.
           MOVE 4 TO RK.
           MOVE "r250" TO R-REC.
           REWRITE R-REC.
           DISPLAY "250 RW " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "251 RN " ST " " RK " " R-REC.
           MOVE 11 TO RK.
           DELETE RELF.
           DISPLAY "252 DEL " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "253 RN " ST " " RK " " R-REC.
           MOVE 2 TO RK.
           MOVE "r254" TO R-REC.
           REWRITE R-REC.
           DISPLAY "254 RW " ST " " RK " " R-REC.
           MOVE 11 TO RK.
           START RELF KEY NOT > RK.
           DISPLAY "255 ST " ST " " RK " " R-REC.
           MOVE 4 TO RK.
           MOVE "w256" TO R-REC.
           WRITE R-REC.
           DISPLAY "256 W " ST " " RK " " R-REC.
           CLOSE RELF. OPEN I-O RELF.
           DISPLAY "257 REOPEN " ST " " RK " " R-REC.
           MOVE 6 TO RK.
           MOVE "w258" TO R-REC.
           WRITE R-REC.
           DISPLAY "258 W " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "259 RN " ST " " RK " " R-REC.
           MOVE 1 TO RK.
           START RELF KEY >= RK.
           DISPLAY "260 ST " ST " " RK " " R-REC.
           MOVE 0 TO RK.
           MOVE "w261" TO R-REC.
           WRITE R-REC.
           DISPLAY "261 W " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "262 RN " ST " " RK " " R-REC.
           MOVE 7 TO RK.
           MOVE "w263" TO R-REC.
           WRITE R-REC.
           DISPLAY "263 W " ST " " RK " " R-REC.
           MOVE 1 TO RK.
           MOVE "w264" TO R-REC.
           WRITE R-REC.
           DISPLAY "264 W " ST " " RK " " R-REC.
           READ RELF PREVIOUS.
           DISPLAY "265 RP " ST " " RK " " R-REC.
           MOVE 6 TO RK.
           START RELF KEY >= RK.
           DISPLAY "266 ST " ST " " RK " " R-REC.
           MOVE 1 TO RK.
           START RELF KEY NOT < RK.
           DISPLAY "267 ST " ST " " RK " " R-REC.
           MOVE 7 TO RK.
           START RELF KEY NOT < RK.
           DISPLAY "268 ST " ST " " RK " " R-REC.
           MOVE 2 TO RK.
           DELETE RELF.
           DISPLAY "269 DEL " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "270 RN " ST " " RK " " R-REC.
           MOVE 8 TO RK.
           MOVE "w271" TO R-REC.
           WRITE R-REC.
           DISPLAY "271 W " ST " " RK " " R-REC.
           CLOSE RELF. OPEN I-O RELF.
           DISPLAY "272 REOPEN " ST " " RK " " R-REC.
           MOVE 2 TO RK.
           START RELF KEY < RK.
           DISPLAY "273 ST " ST " " RK " " R-REC.
           MOVE 11 TO RK.
           READ RELF.
           DISPLAY "274 RK " ST " " RK " " R-REC.
           MOVE 10 TO RK.
           MOVE "w275" TO R-REC.
           WRITE R-REC.
           DISPLAY "275 W " ST " " RK " " R-REC.
           READ RELF PREVIOUS.
           DISPLAY "276 RP " ST " " RK " " R-REC.
           MOVE 1 TO RK.
           MOVE "r277" TO R-REC.
           REWRITE R-REC.
           DISPLAY "277 RW " ST " " RK " " R-REC.
           CLOSE RELF. OPEN I-O RELF.
           DISPLAY "278 REOPEN " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "279 RN " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "280 RN " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "281 RN " ST " " RK " " R-REC.
           MOVE 8 TO RK.
           MOVE "r282" TO R-REC.
           REWRITE R-REC.
           DISPLAY "282 RW " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "283 RN " ST " " RK " " R-REC.
           MOVE 0 TO RK.
           DELETE RELF.
           DISPLAY "284 DEL " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "285 RN " ST " " RK " " R-REC.
           MOVE 8 TO RK.
           MOVE "r286" TO R-REC.
           REWRITE R-REC.
           DISPLAY "286 RW " ST " " RK " " R-REC.
           MOVE 9 TO RK.
           MOVE "r287" TO R-REC.
           REWRITE R-REC.
           DISPLAY "287 RW " ST " " RK " " R-REC.
           MOVE 10 TO RK.
           MOVE "w288" TO R-REC.
           WRITE R-REC.
           DISPLAY "288 W " ST " " RK " " R-REC.
           MOVE 4 TO RK.
           START RELF KEY NOT > RK.
           DISPLAY "289 ST " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "290 RN " ST " " RK " " R-REC.
           MOVE 8 TO RK.
           MOVE "r291" TO R-REC.
           REWRITE R-REC.
           DISPLAY "291 RW " ST " " RK " " R-REC.
           MOVE 5 TO RK.
           START RELF KEY <= RK.
           DISPLAY "292 ST " ST " " RK " " R-REC.
           MOVE 7 TO RK.
           MOVE "w293" TO R-REC.
           WRITE R-REC.
           DISPLAY "293 W " ST " " RK " " R-REC.
           CLOSE RELF. OPEN I-O RELF.
           DISPLAY "294 REOPEN " ST " " RK " " R-REC.
           MOVE 5 TO RK.
           DELETE RELF.
           DISPLAY "295 DEL " ST " " RK " " R-REC.
           MOVE 9 TO RK.
           MOVE "w296" TO R-REC.
           WRITE R-REC.
           DISPLAY "296 W " ST " " RK " " R-REC.
           MOVE 8 TO RK.
           MOVE "r297" TO R-REC.
           REWRITE R-REC.
           DISPLAY "297 RW " ST " " RK " " R-REC.
           MOVE 9 TO RK.
           DELETE RELF.
           DISPLAY "298 DEL " ST " " RK " " R-REC.
           CLOSE RELF. OPEN I-O RELF.
           DISPLAY "299 REOPEN " ST " " RK " " R-REC.
           MOVE 6 TO RK.
           MOVE "w300" TO R-REC.
           WRITE R-REC.
           DISPLAY "300 W " ST " " RK " " R-REC.
           MOVE 11 TO RK.
           READ RELF.
           DISPLAY "301 RK " ST " " RK " " R-REC.
           MOVE 3 TO RK.
           MOVE "w302" TO R-REC.
           WRITE R-REC.
           DISPLAY "302 W " ST " " RK " " R-REC.
           MOVE 11 TO RK.
           MOVE "w303" TO R-REC.
           WRITE R-REC.
           DISPLAY "303 W " ST " " RK " " R-REC.
           MOVE 11 TO RK.
           MOVE "w304" TO R-REC.
           WRITE R-REC.
           DISPLAY "304 W " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "305 RN " ST " " RK " " R-REC.
           READ RELF PREVIOUS.
           DISPLAY "306 RP " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "307 RN " ST " " RK " " R-REC.
           CLOSE RELF.
           OPEN I-O QFILE.
           DISPLAY "308 QOPEN " QS.
           DELETE QFILE.
           DISPLAY "309 DEL Q" QS " " QK " " Q-REC.
           READ QFILE.
           DISPLAY "310 R Q" QS " " QK " " Q-REC.
           MOVE "q311" TO Q-REC.
           REWRITE Q-REC.
           DISPLAY "311 RW Q" QS " " QK " " Q-REC.
           MOVE "q312" TO Q-REC.
           REWRITE Q-REC.
           DISPLAY "312 RW Q" QS " " QK " " Q-REC.
           MOVE "q313" TO Q-REC.
           REWRITE Q-REC.
           DISPLAY "313 RW Q" QS " " QK " " Q-REC.
           DELETE QFILE.
           DISPLAY "314 DEL Q" QS " " QK " " Q-REC.
           CLOSE QFILE. OPEN I-O RELF.
           DISPLAY "315 BACK Q" QS " " QK " " Q-REC.
           MOVE 5 TO RK.
           READ RELF.
           DISPLAY "316 RK " ST " " RK " " R-REC.
           READ RELF PREVIOUS.
           DISPLAY "317 RP " ST " " RK " " R-REC.
           MOVE 9 TO RK.
           START RELF KEY NOT < RK.
           DISPLAY "318 ST " ST " " RK " " R-REC.
           MOVE 6 TO RK.
           READ RELF.
           DISPLAY "319 RK " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "320 RN " ST " " RK " " R-REC.
           MOVE 0 TO RK.
           DELETE RELF.
           DISPLAY "321 DEL " ST " " RK " " R-REC.
           MOVE 4 TO RK.
           MOVE "r322" TO R-REC.
           REWRITE R-REC.
           DISPLAY "322 RW " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "323 RN " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "324 RN " ST " " RK " " R-REC.
           MOVE 11 TO RK.
           MOVE "w325" TO R-REC.
           WRITE R-REC.
           DISPLAY "325 W " ST " " RK " " R-REC.
           MOVE 3 TO RK.
           READ RELF.
           DISPLAY "326 RK " ST " " RK " " R-REC.
           MOVE 11 TO RK.
           MOVE "w327" TO R-REC.
           WRITE R-REC.
           DISPLAY "327 W " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "328 RN " ST " " RK " " R-REC.
           MOVE 4 TO RK.
           MOVE "w329" TO R-REC.
           WRITE R-REC.
           DISPLAY "329 W " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "330 RN " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "331 RN " ST " " RK " " R-REC.
           READ RELF PREVIOUS.
           DISPLAY "332 RP " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "333 RN " ST " " RK " " R-REC.
           MOVE 3 TO RK.
           MOVE "w334" TO R-REC.
           WRITE R-REC.
           DISPLAY "334 W " ST " " RK " " R-REC.
           CLOSE RELF. OPEN I-O RELF.
           DISPLAY "335 REOPEN " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "336 RN " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "337 RN " ST " " RK " " R-REC.
           MOVE 0 TO RK.
           START RELF KEY >= RK.
           DISPLAY "338 ST " ST " " RK " " R-REC.
           MOVE 2 TO RK.
           DELETE RELF.
           DISPLAY "339 DEL " ST " " RK " " R-REC.
           MOVE 1 TO RK.
           DELETE RELF.
           DISPLAY "340 DEL " ST " " RK " " R-REC.
           MOVE 8 TO RK.
           START RELF KEY > RK.
           DISPLAY "341 ST " ST " " RK " " R-REC.
           MOVE 4 TO RK.
           MOVE "w342" TO R-REC.
           WRITE R-REC.
           DISPLAY "342 W " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "343 RN " ST " " RK " " R-REC.
           MOVE 10 TO RK.
           START RELF KEY NOT > RK.
           DISPLAY "344 ST " ST " " RK " " R-REC.
           MOVE 8 TO RK.
           START RELF KEY >= RK.
           DISPLAY "345 ST " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "346 RN " ST " " RK " " R-REC.
           CLOSE RELF. OPEN I-O RELF.
           DISPLAY "347 REOPEN " ST " " RK " " R-REC.
           MOVE 2 TO RK.
           READ RELF.
           DISPLAY "348 RK " ST " " RK " " R-REC.
           MOVE 5 TO RK.
           DELETE RELF.
           DISPLAY "349 DEL " ST " " RK " " R-REC.
           MOVE 10 TO RK.
           START RELF KEY = RK.
           DISPLAY "350 ST " ST " " RK " " R-REC.
           MOVE 11 TO RK.
           START RELF KEY NOT < RK.
           DISPLAY "351 ST " ST " " RK " " R-REC.
           MOVE 2 TO RK.
           MOVE "w352" TO R-REC.
           WRITE R-REC.
           DISPLAY "352 W " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "353 RN " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "354 RN " ST " " RK " " R-REC.
           MOVE 1 TO RK.
           MOVE "w355" TO R-REC.
           WRITE R-REC.
           DISPLAY "355 W " ST " " RK " " R-REC.
           MOVE 9 TO RK.
           READ RELF.
           DISPLAY "356 RK " ST " " RK " " R-REC.
           MOVE 3 TO RK.
           READ RELF.
           DISPLAY "357 RK " ST " " RK " " R-REC.
           MOVE 2 TO RK.
           MOVE "r358" TO R-REC.
           REWRITE R-REC.
           DISPLAY "358 RW " ST " " RK " " R-REC.
           MOVE 9 TO RK.
           MOVE "r359" TO R-REC.
           REWRITE R-REC.
           DISPLAY "359 RW " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "360 RN " ST " " RK " " R-REC.
           MOVE 6 TO RK.
           MOVE "r361" TO R-REC.
           REWRITE R-REC.
           DISPLAY "361 RW " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "362 RN " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "363 RN " ST " " RK " " R-REC.
           MOVE 1 TO RK.
           MOVE "r364" TO R-REC.
           REWRITE R-REC.
           DISPLAY "364 RW " ST " " RK " " R-REC.
           MOVE 7 TO RK.
           MOVE "w365" TO R-REC.
           WRITE R-REC.
           DISPLAY "365 W " ST " " RK " " R-REC.
           MOVE 11 TO RK.
           MOVE "w366" TO R-REC.
           WRITE R-REC.
           DISPLAY "366 W " ST " " RK " " R-REC.
           READ RELF PREVIOUS.
           DISPLAY "367 RP " ST " " RK " " R-REC.
           MOVE 5 TO RK.
           DELETE RELF.
           DISPLAY "368 DEL " ST " " RK " " R-REC.
           MOVE 8 TO RK.
           MOVE "w369" TO R-REC.
           WRITE R-REC.
           DISPLAY "369 W " ST " " RK " " R-REC.
           MOVE 5 TO RK.
           MOVE "r370" TO R-REC.
           REWRITE R-REC.
           DISPLAY "370 RW " ST " " RK " " R-REC.
           MOVE 10 TO RK.
           DELETE RELF.
           DISPLAY "371 DEL " ST " " RK " " R-REC.
           MOVE 7 TO RK.
           MOVE "w372" TO R-REC.
           WRITE R-REC.
           DISPLAY "372 W " ST " " RK " " R-REC.
           MOVE 9 TO RK.
           DELETE RELF.
           DISPLAY "373 DEL " ST " " RK " " R-REC.
           MOVE 6 TO RK.
           START RELF KEY > RK.
           DISPLAY "374 ST " ST " " RK " " R-REC.
           MOVE 7 TO RK.
           START RELF KEY > RK.
           DISPLAY "375 ST " ST " " RK " " R-REC.
           MOVE 1 TO RK.
           READ RELF.
           DISPLAY "376 RK " ST " " RK " " R-REC.
           MOVE 7 TO RK.
           MOVE "w377" TO R-REC.
           WRITE R-REC.
           DISPLAY "377 W " ST " " RK " " R-REC.
           CLOSE RELF. OPEN I-O RELF.
           DISPLAY "378 REOPEN " ST " " RK " " R-REC.
           MOVE 3 TO RK.
           MOVE "w379" TO R-REC.
           WRITE R-REC.
           DISPLAY "379 W " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "380 RN " ST " " RK " " R-REC.
           MOVE 3 TO RK.
           MOVE "w381" TO R-REC.
           WRITE R-REC.
           DISPLAY "381 W " ST " " RK " " R-REC.
           MOVE 3 TO RK.
           START RELF KEY NOT > RK.
           DISPLAY "382 ST " ST " " RK " " R-REC.
           CLOSE RELF. OPEN I-O RELF.
           DISPLAY "383 REOPEN " ST " " RK " " R-REC.
           MOVE 5 TO RK.
           START RELF KEY < RK.
           DISPLAY "384 ST " ST " " RK " " R-REC.
           MOVE 2 TO RK.
           START RELF KEY < RK.
           DISPLAY "385 ST " ST " " RK " " R-REC.
           MOVE 9 TO RK.
           MOVE "r386" TO R-REC.
           REWRITE R-REC.
           DISPLAY "386 RW " ST " " RK " " R-REC.
           MOVE 4 TO RK.
           START RELF KEY <= RK.
           DISPLAY "387 ST " ST " " RK " " R-REC.
           MOVE 6 TO RK.
           MOVE "w388" TO R-REC.
           WRITE R-REC.
           DISPLAY "388 W " ST " " RK " " R-REC.
           MOVE 8 TO RK.
           MOVE "w389" TO R-REC.
           WRITE R-REC.
           DISPLAY "389 W " ST " " RK " " R-REC.
           MOVE 4 TO RK.
           MOVE "w390" TO R-REC.
           WRITE R-REC.
           DISPLAY "390 W " ST " " RK " " R-REC.
           READ RELF PREVIOUS.
           DISPLAY "391 RP " ST " " RK " " R-REC.
           MOVE 9 TO RK.
           READ RELF.
           DISPLAY "392 RK " ST " " RK " " R-REC.
           MOVE 5 TO RK.
           MOVE "r393" TO R-REC.
           REWRITE R-REC.
           DISPLAY "393 RW " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "394 RN " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "395 RN " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "396 RN " ST " " RK " " R-REC.
           CLOSE RELF. OPEN I-O RELF.
           DISPLAY "397 REOPEN " ST " " RK " " R-REC.
           MOVE 3 TO RK.
           READ RELF.
           DISPLAY "398 RK " ST " " RK " " R-REC.
           MOVE 7 TO RK.
           MOVE "r399" TO R-REC.
           REWRITE R-REC.
           DISPLAY "399 RW " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "400 RN " ST " " RK " " R-REC.
           MOVE 0 TO RK.
           START RELF KEY < RK.
           DISPLAY "401 ST " ST " " RK " " R-REC.
           MOVE 5 TO RK.
           START RELF KEY = RK.
           DISPLAY "402 ST " ST " " RK " " R-REC.
           MOVE 9 TO RK.
           MOVE "w403" TO R-REC.
           WRITE R-REC.
           DISPLAY "403 W " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "404 RN " ST " " RK " " R-REC.
           MOVE 0 TO RK.
           START RELF KEY <= RK.
           DISPLAY "405 ST " ST " " RK " " R-REC.
           MOVE 10 TO RK.
           READ RELF.
           DISPLAY "406 RK " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "407 RN " ST " " RK " " R-REC.
           MOVE 5 TO RK.
           MOVE "r408" TO R-REC.
           REWRITE R-REC.
           DISPLAY "408 RW " ST " " RK " " R-REC.
           MOVE 8 TO RK.
           MOVE "r409" TO R-REC.
           REWRITE R-REC.
           DISPLAY "409 RW " ST " " RK " " R-REC.
           CLOSE RELF. OPEN I-O RELF.
           DISPLAY "410 REOPEN " ST " " RK " " R-REC.
           MOVE 3 TO RK.
           READ RELF.
           DISPLAY "411 RK " ST " " RK " " R-REC.
           MOVE 11 TO RK.
           DELETE RELF.
           DISPLAY "412 DEL " ST " " RK " " R-REC.
           READ RELF PREVIOUS.
           DISPLAY "413 RP " ST " " RK " " R-REC.
           MOVE 10 TO RK.
           MOVE "w414" TO R-REC.
           WRITE R-REC.
           DISPLAY "414 W " ST " " RK " " R-REC.
           CLOSE RELF.
           OPEN I-O QFILE.
           DISPLAY "415 QOPEN " QS.
           READ QFILE.
           DISPLAY "416 R Q" QS " " QK " " Q-REC.
           CLOSE QFILE. OPEN I-O RELF.
           DISPLAY "417 BACK Q" QS " " QK " " Q-REC.
           MOVE 11 TO RK.
           READ RELF.
           DISPLAY "418 RK " ST " " RK " " R-REC.
           MOVE 5 TO RK.
           MOVE "w419" TO R-REC.
           WRITE R-REC.
           DISPLAY "419 W " ST " " RK " " R-REC.
           MOVE 5 TO RK.
           START RELF KEY > RK.
           DISPLAY "420 ST " ST " " RK " " R-REC.
           MOVE 2 TO RK.
           MOVE "r421" TO R-REC.
           REWRITE R-REC.
           DISPLAY "421 RW " ST " " RK " " R-REC.
           MOVE 8 TO RK.
           DELETE RELF.
           DISPLAY "422 DEL " ST " " RK " " R-REC.
           CLOSE RELF.
           OPEN EXTEND QFILE.
           DISPLAY "423 QOPEN " QS.
           READ QFILE.
           DISPLAY "424 R Q" QS " " QK " " Q-REC.
           READ QFILE.
           DISPLAY "425 R Q" QS " " QK " " Q-REC.
           DELETE QFILE.
           DISPLAY "426 DEL Q" QS " " QK " " Q-REC.
           READ QFILE.
           DISPLAY "427 R Q" QS " " QK " " Q-REC.
           MOVE 1 TO QK.
           START QFILE KEY <= QK.
           DISPLAY "428 ST Q" QS " " QK " " Q-REC.
           CLOSE QFILE. OPEN I-O RELF.
           DISPLAY "429 BACK Q" QS " " QK " " Q-REC.
           CLOSE RELF.
           OPEN I-O QFILE.
           DISPLAY "430 QOPEN " QS.
           READ QFILE.
           DISPLAY "431 R Q" QS " " QK " " Q-REC.
           MOVE 4 TO QK.
           START QFILE KEY <= QK.
           DISPLAY "432 ST Q" QS " " QK " " Q-REC.
           MOVE "q433" TO Q-REC.
           WRITE Q-REC.
           DISPLAY "433 W Q" QS " " QK " " Q-REC.
           MOVE "q434" TO Q-REC.
           WRITE Q-REC.
           DISPLAY "434 W Q" QS " " QK " " Q-REC.
           READ QFILE.
           DISPLAY "435 R Q" QS " " QK " " Q-REC.
           MOVE "q436" TO Q-REC.
           REWRITE Q-REC.
           DISPLAY "436 RW Q" QS " " QK " " Q-REC.
           MOVE "q437" TO Q-REC.
           WRITE Q-REC.
           DISPLAY "437 W Q" QS " " QK " " Q-REC.
           DELETE QFILE.
           DISPLAY "438 DEL Q" QS " " QK " " Q-REC.
           READ QFILE.
           DISPLAY "439 R Q" QS " " QK " " Q-REC.
           MOVE "q440" TO Q-REC.
           REWRITE Q-REC.
           DISPLAY "440 RW Q" QS " " QK " " Q-REC.
           MOVE "q441" TO Q-REC.
           WRITE Q-REC.
           DISPLAY "441 W Q" QS " " QK " " Q-REC.
           MOVE 2 TO QK.
           START QFILE KEY >= QK.
           DISPLAY "442 ST Q" QS " " QK " " Q-REC.
           READ QFILE.
           DISPLAY "443 R Q" QS " " QK " " Q-REC.
           READ QFILE.
           DISPLAY "444 R Q" QS " " QK " " Q-REC.
           READ QFILE.
           DISPLAY "445 R Q" QS " " QK " " Q-REC.
           READ QFILE.
           DISPLAY "446 R Q" QS " " QK " " Q-REC.
           READ QFILE.
           DISPLAY "447 R Q" QS " " QK " " Q-REC.
           READ QFILE.
           DISPLAY "448 R Q" QS " " QK " " Q-REC.
           MOVE "q449" TO Q-REC.
           WRITE Q-REC.
           DISPLAY "449 W Q" QS " " QK " " Q-REC.
           CLOSE QFILE. OPEN I-O RELF.
           DISPLAY "450 BACK Q" QS " " QK " " Q-REC.
           MOVE 0 TO RK.
           MOVE "w451" TO R-REC.
           WRITE R-REC.
           DISPLAY "451 W " ST " " RK " " R-REC.
           MOVE 6 TO RK.
           MOVE "w452" TO R-REC.
           WRITE R-REC.
           DISPLAY "452 W " ST " " RK " " R-REC.
           MOVE 2 TO RK.
           MOVE "r453" TO R-REC.
           REWRITE R-REC.
           DISPLAY "453 RW " ST " " RK " " R-REC.
           MOVE 8 TO RK.
           START RELF KEY NOT > RK.
           DISPLAY "454 ST " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "455 RN " ST " " RK " " R-REC.
           MOVE 2 TO RK.
           MOVE "r456" TO R-REC.
           REWRITE R-REC.
           DISPLAY "456 RW " ST " " RK " " R-REC.
           MOVE 3 TO RK.
           START RELF KEY <= RK.
           DISPLAY "457 ST " ST " " RK " " R-REC.
           MOVE 2 TO RK.
           READ RELF.
           DISPLAY "458 RK " ST " " RK " " R-REC.
           READ RELF PREVIOUS.
           DISPLAY "459 RP " ST " " RK " " R-REC.
           CLOSE RELF.
           OPEN I-O QFILE.
           DISPLAY "460 QOPEN " QS.
           MOVE "q461" TO Q-REC.
           WRITE Q-REC.
           DISPLAY "461 W Q" QS " " QK " " Q-REC.
           MOVE 10 TO QK.
           START QFILE KEY < QK.
           DISPLAY "462 ST Q" QS " " QK " " Q-REC.
           CLOSE QFILE. OPEN I-O RELF.
           DISPLAY "463 BACK Q" QS " " QK " " Q-REC.
           MOVE 10 TO RK.
           DELETE RELF.
           DISPLAY "464 DEL " ST " " RK " " R-REC.
           MOVE 3 TO RK.
           MOVE "w465" TO R-REC.
           WRITE R-REC.
           DISPLAY "465 W " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "466 RN " ST " " RK " " R-REC.
           MOVE 4 TO RK.
           START RELF KEY < RK.
           DISPLAY "467 ST " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "468 RN " ST " " RK " " R-REC.
           READ RELF PREVIOUS.
           DISPLAY "469 RP " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "470 RN " ST " " RK " " R-REC.
           CLOSE RELF. OPEN I-O RELF.
           DISPLAY "471 REOPEN " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "472 RN " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "473 RN " ST " " RK " " R-REC.
           MOVE 4 TO RK.
           START RELF KEY NOT > RK.
           DISPLAY "474 ST " ST " " RK " " R-REC.
           MOVE 3 TO RK.
           START RELF KEY = RK.
           DISPLAY "475 ST " ST " " RK " " R-REC.
           MOVE 3 TO RK.
           MOVE "r476" TO R-REC.
           REWRITE R-REC.
           DISPLAY "476 RW " ST " " RK " " R-REC.
           MOVE 8 TO RK.
           MOVE "w477" TO R-REC.
           WRITE R-REC.
           DISPLAY "477 W " ST " " RK " " R-REC.
           MOVE 4 TO RK.
           READ RELF.
           DISPLAY "478 RK " ST " " RK " " R-REC.
           READ RELF PREVIOUS.
           DISPLAY "479 RP " ST " " RK " " R-REC.
           READ RELF PREVIOUS.
           DISPLAY "480 RP " ST " " RK " " R-REC.
           CLOSE RELF.
           OPEN INPUT QFILE.
           DISPLAY "481 QOPEN " QS.
           MOVE 3 TO QK.
           START QFILE KEY > QK.
           DISPLAY "482 ST Q" QS " " QK " " Q-REC.
           MOVE "q483" TO Q-REC.
           WRITE Q-REC.
           DISPLAY "483 W Q" QS " " QK " " Q-REC.
           MOVE "q484" TO Q-REC.
           WRITE Q-REC.
           DISPLAY "484 W Q" QS " " QK " " Q-REC.
           CLOSE QFILE. OPEN I-O RELF.
           DISPLAY "485 BACK Q" QS " " QK " " Q-REC.
           READ RELF NEXT.
           DISPLAY "486 RN " ST " " RK " " R-REC.
           MOVE 9 TO RK.
           READ RELF.
           DISPLAY "487 RK " ST " " RK " " R-REC.
           READ RELF NEXT.
           DISPLAY "488 RN " ST " " RK " " R-REC.
           MOVE 1 TO RK.
           START RELF KEY > RK.
           DISPLAY "489 ST " ST " " RK " " R-REC.
           MOVE 11 TO RK.
           MOVE "r490" TO R-REC.
           REWRITE R-REC.
           DISPLAY "490 RW " ST " " RK " " R-REC.
           READ RELF PREVIOUS.
           DISPLAY "491 RP " ST " " RK " " R-REC.
           MOVE 10 TO RK.
           MOVE "w492" TO R-REC.
           WRITE R-REC.
           DISPLAY "492 W " ST " " RK " " R-REC.
           READ RELF PREVIOUS.
           DISPLAY "493 RP " ST " " RK " " R-REC.
           MOVE 10 TO RK.
           START RELF KEY NOT > RK.
           DISPLAY "494 ST " ST " " RK " " R-REC.
           MOVE 10 TO RK.
           MOVE "w495" TO R-REC.
           WRITE R-REC.
           DISPLAY "495 W " ST " " RK " " R-REC.
           MOVE 1 TO RK.
           MOVE "w496" TO R-REC.
           WRITE R-REC.
           DISPLAY "496 W " ST " " RK " " R-REC.
           MOVE 4 TO RK.
           MOVE "w497" TO R-REC.
           WRITE R-REC.
           DISPLAY "497 W " ST " " RK " " R-REC.
           MOVE 9 TO RK.
           READ RELF.
           DISPLAY "498 RK " ST " " RK " " R-REC.
           MOVE 8 TO RK.
           START RELF KEY = RK.
           DISPLAY "499 ST " ST " " RK " " R-REC.
           CLOSE RELF.
           CLOSE QFILE.
           STOP RUN.
