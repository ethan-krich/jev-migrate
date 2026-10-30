       IDENTIFICATION DIVISION.
       PROGRAM-ID. INITODO.
      * INITIALIZE of groups with OCCURS DEPENDING ON.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 CNT  PIC 9 VALUE 3.
       01 ODO1.
          05 OD1 PIC X(2) OCCURS 1 TO 5 DEPENDING ON CNT VALUE "ab".
       01 ODO2.
          05 HDR PIC X VALUE "h".
          05 OD2 OCCURS 1 TO 5 DEPENDING ON CNT.
             10 OA PIC X VALUE "a".
             10 ON9 PIC 9 VALUE 7.
       01 ODO3.
          05 OD3 OCCURS 1 TO 5 DEPENDING ON CNT.
             10 OB PIC X VALUE "b".
             10 OC PIC X(2) VALUE "cc".
             10 FILLER PIC X VALUE "f".
       PROCEDURE DIVISION.
       MAIN-PARA.
           INITIALIZE ODO1 ODO2 ODO3.
           MOVE 5 TO CNT.
           DISPLAY "[" ODO1 "] [" ODO2 "] [" ODO3 "]".
           MOVE 2 TO CNT.
           INITIALIZE ODO2 REPLACING NUMERIC BY 4.
           MOVE 5 TO CNT.
           DISPLAY "[" ODO2 "]".
           MOVE 4 TO CNT.
           INITIALIZE ODO3 WITH FILLER REPLACING ALPHANUMERIC BY "z".
           MOVE 5 TO CNT.
           DISPLAY "[" ODO3 "]".
           MOVE 1 TO CNT.
           INITIALIZE ODO1.
           MOVE 5 TO CNT.
           DISPLAY "[" ODO1 "]".
           INITIALIZE OD2 (5).
           DISPLAY "[" ODO2 "]".
           STOP RUN.
