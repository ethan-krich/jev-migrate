       IDENTIFICATION DIVISION.
       PROGRAM-ID. SWTEST.
      * SWITCH-n ON / OFF STATUS and SET switch TO ON / OFF.
       ENVIRONMENT DIVISION.
       CONFIGURATION SECTION.
       SPECIAL-NAMES.
           SWITCH-1 IS TRACE-SW ON STATUS IS TRACE-ON
                               OFF STATUS IS TRACE-OFF
           SWITCH-2 IS SW2 ON STATUS IS SW2-ON
           SWITCH-3 IS SW3 OFF STATUS IS SW3-OFF
           SWITCH-36 IS SW36 ON IS SW36-ON
           SWITCH-5 IS SW5 ON STATUS IS SW5-ON.
       PROCEDURE DIVISION.
       MAIN-PARA.
           PERFORM SHOW.
           SET TRACE-SW TO OFF.
           SET SW2 SW5 TO ON.
           SET SW3 TO OFF.
           PERFORM SHOW.
           EVALUATE TRUE
               WHEN SW2-ON DISPLAY "evaluate sw2"
               WHEN OTHER DISPLAY "evaluate other"
           END-EVALUATE.
           EVALUATE FALSE
               WHEN TRACE-ON DISPLAY "trace is off"
           END-EVALUATE.
           STOP RUN.
       SHOW.
           IF TRACE-ON DISPLAY "trace on" END-IF.
           IF TRACE-OFF DISPLAY "trace off" END-IF.
           IF SW2-ON DISPLAY "sw2 on" ELSE DISPLAY "sw2 off" END-IF.
           IF SW3-OFF DISPLAY "sw3 off" ELSE DISPLAY "sw3 on" END-IF.
           IF SW36-ON AND NOT SW5-ON DISPLAY "sw36 only" END-IF.
           IF SW5-ON DISPLAY "sw5 on" END-IF.
