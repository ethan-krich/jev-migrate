       IDENTIFICATION DIVISION.
       PROGRAM-ID. USAGES.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 G.
          05 P1 PIC S9(5) COMP-3 VALUE -123.
          05 P2 PIC 9(3) PACKED-DECIMAL VALUE 123.
          05 P3 PIC S9(4)V9 COMP-3 VALUE 12.5.
          05 B1 PIC S9(4) COMP VALUE -2.
          05 B2 PIC 9(4) BINARY VALUE 258.
          05 B3 PIC S9(9) COMP-4 VALUE 65536.
          05 B4 PIC 9(2) COMP VALUE 7.
          05 B5 PIC S9(18) COMP VALUE -1.
          05 C5 PIC 9(4) COMP-5 VALUE 258.
          05 CX PIC 9(4) COMP-X VALUE 258.
          05 IX USAGE INDEX.
       01 GC USAGE COMP.
          05 GC1 PIC 9(4) VALUE 65.
          05 GC2 PIC 9(4) VALUE 66.
       01 K   PIC 9(4) COMP.
       01 K5  PIC 9(4) COMP-5.
       01 KS  PIC S9(4) COMP-5.
       01 PK  PIC S9(3)V99 COMP-3.
       01 Z   PIC S9(5)V99.
       01 T.
          05 TE OCCURS 3 INDEXED BY TX.
             10 TV PIC 9(3) COMP-3.
       PROCEDURE DIVISION.
           DISPLAY "[" G "]".
           DISPLAY "[" GC "]".
           DISPLAY P1 " " P2 " " P3 " " B1 " " B2 " " B3 " " B4.
           DISPLAY B5 " " C5 " " CX " " IX.
           MOVE 12345 TO K.
           DISPLAY K.
           MOVE 12345 TO K5.
           DISPLAY K5.
           MOVE -5 TO K5.
           DISPLAY K5.
           MOVE 9999 TO K.
           ADD 1 TO K ON SIZE ERROR DISPLAY "size K".
           DISPLAY K.
           MOVE 9999 TO K5.
           ADD 1 TO K5 ON SIZE ERROR DISPLAY "size K5".
           DISPLAY K5.
           MOVE 65535 TO K5.
           ADD 1 TO K5 ON SIZE ERROR DISPLAY "size K5 again".
           DISPLAY K5.
           MOVE 32767 TO KS.
           ADD 1 TO KS ON SIZE ERROR DISPLAY "size KS".
           DISPLAY KS.
           MOVE 1234.567 TO PK.
           DISPLAY PK.
           COMPUTE Z = P1 * B1 + P3 - PK.
           DISPLAY Z.
           MULTIPLY B2 BY P3 ROUNDED.
           DISPLAY P3.
           SET IX TO 2.
           SET TX TO IX.
           SET IX UP BY 2.
           DISPLAY IX.
           MOVE 7 TO TV(TX).
           DISPLAY TV(1) " " TV(2) " " TV(3).
           IF P1 < B1 DISPLAY "p1 < b1".
           IF PK NUMERIC DISPLAY "pk numeric".
           MOVE B2 TO P2.
           MOVE P2 TO Z.
           DISPLAY Z.
           STOP RUN.
