       IDENTIFICATION DIVISION.
       PROGRAM-ID. ALPHNUM.
      * Alphanumeric data moved into numeric items.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 TXT       PIC X(12).
       01 N4        PIC 9(4).
       01 S4        PIC S9(4).
       01 D52       PIC S9(3)V99.
       01 U32       PIC 9(3)V99.
       01 SL        PIC S99V9 SIGN LEADING SEPARATE.
       01 ST        PIC S9(3) SIGN TRAILING SEPARATE.
       01 P3        PIC S9(5)V99 COMP-3.
       01 B4        PIC S9(4) COMP.
       01 B9        PIC 9(9) COMP.
       01 I         PIC 99.
       01 SAMPLES.
          05 FILLER PIC X(12) VALUE "123".
          05 FILLER PIC X(12) VALUE "  -12.5".
          05 FILLER PIC X(12) VALUE "+7".
          05 FILLER PIC X(12) VALUE "1,234.567".
          05 FILLER PIC X(12) VALUE "12A".
          05 FILLER PIC X(12) VALUE "1.2.3".
          05 FILLER PIC X(12) VALUE "98765432".
          05 FILLER PIC X(12) VALUE ".75".
          05 FILLER PIC X(12) VALUE "-".
          05 FILLER PIC X(12) VALUE "- 4 5".
          05 FILLER PIC X(12) VALUE "12.5X".
          05 FILLER PIC X(12) VALUE "-0".
          05 FILLER PIC X(12) VALUE SPACES.
          05 FILLER PIC X(12) VALUE "99999.999".
       01 SAMPLE-TABLE REDEFINES SAMPLES.
          05 SAMPLE PIC X(12) OCCURS 14.
       01 CSV       PIC X(30) VALUE "42,-3.5,,7x,1234567,+19".
       01 FIXREC    PIC X(10) VALUE "0012345678".
       PROCEDURE DIVISION.
       MAIN-PARA.
           PERFORM VARYING I FROM 1 BY 1 UNTIL I > 14
               MOVE SAMPLE (I) TO TXT
               MOVE TXT TO N4
               MOVE TXT TO S4
               MOVE TXT TO D52
               MOVE TXT TO U32
               MOVE TXT TO SL
               MOVE TXT TO ST
               MOVE TXT TO P3
               MOVE TXT TO B4
               MOVE TXT TO B9
               DISPLAY I " [" TXT "] " N4 " " S4 " " D52 " " U32 " "
                       SL " " ST
               DISPLAY "   " P3 " " B4 " " B9
           END-PERFORM.
           MOVE "  42.9" TO TXT.
           MOVE TXT (3:2) TO N4.
           MOVE TXT (1:6) TO D52.
           DISPLAY "refmod " N4 " " D52.
           MOVE "123" TO N4.
           MOVE "12A" TO S4.
           DISPLAY "lit " N4 " " S4.
           MOVE "1234567" TO N4.
           MOVE " -12.5" TO D52.
           MOVE "-3" TO SL.
           DISPLAY "lit " N4 " " D52 " " SL.
           MOVE ALL "12" TO N4.
           MOVE ALL "7" TO D52.
           MOVE ALL "123" TO P3.
           MOVE ALL "5" TO B9.
           DISPLAY "all " N4 " " D52 " " P3 " " B9.
           UNSTRING CSV DELIMITED BY "," INTO N4 D52 S4 U32 B9 SL.
           DISPLAY "unstring " N4 " " D52 " " S4 " " U32 " " B9 " " SL.
           UNSTRING FIXREC INTO N4 P3 B4.
           DISPLAY "fixed " N4 " " P3 " " B4.
           STOP RUN.
