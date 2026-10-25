       IDENTIFICATION DIVISION.
       PROGRAM-ID. ENVS.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 V   PIC X(10) VALUE "init".
       01 SV  PIC X(3).
       01 N   PIC 9(3).
       01 NAM PIC X(12) VALUE "MYVAR".
       PROCEDURE DIVISION.
           DISPLAY "MYVAR" UPON ENVIRONMENT-NAME.
           ACCEPT V FROM ENVIRONMENT-VALUE
             ON EXCEPTION DISPLAY "unset" END-DISPLAY
             NOT ON EXCEPTION DISPLAY "set [" V "]" END-DISPLAY
           END-ACCEPT.
           ACCEPT SV FROM ENVIRONMENT-VALUE.
           DISPLAY "short [" SV "]".
           DISPLAY "NOPE" UPON ENVIRONMENT-NAME.
           ACCEPT V FROM ENVIRONMENT-VALUE
             ON EXCEPTION DISPLAY "nope unset [" V "]" END-DISPLAY
             NOT ON EXCEPTION DISPLAY "nope set" END-DISPLAY
           END-ACCEPT.
           MOVE "keep" TO V.
           ACCEPT V FROM ENVIRONMENT-VALUE.
           DISPLAY "no phrase [" V "]".
           DISPLAY NAM UPON ENVIRONMENT-NAME.
           DISPLAY "new value" UPON ENVIRONMENT-VALUE.
           ACCEPT V FROM ENVIRONMENT-VALUE.
           DISPLAY "after set [" V "]".
           DISPLAY "EMPTYV" UPON ENVIRONMENT-NAME.
           ACCEPT V FROM ENVIRONMENT-VALUE
             ON EXCEPTION DISPLAY "empty unset [" V "]" END-DISPLAY
             NOT ON EXCEPTION DISPLAY "empty set [" V "]" END-DISPLAY
           END-ACCEPT.
           ACCEPT N FROM ARGUMENT-NUMBER.
           DISPLAY "args " N.
           ACCEPT V FROM COMMAND-LINE.
           DISPLAY "cmd [" V "]".
           ACCEPT V FROM ARGUMENT-VALUE
             ON EXCEPTION DISPLAY "no arg [" V "]" END-DISPLAY
           END-ACCEPT.
           ACCEPT V FROM ENVIRONMENT "MYVAR".
           DISPLAY "named [" V "]".
           ACCEPT V FROM ENVIRONMENT "UNSET-VAR"
             ON EXCEPTION DISPLAY "named unset [" V "]" END-DISPLAY
           END-ACCEPT.
           DISPLAY 1 UPON ARGUMENT-NUMBER.
           MOVE "same" TO V.
           ACCEPT V FROM ARGUMENT-VALUE
             ON EXCEPTION DISPLAY "still no arg [" V "]" END-DISPLAY
             NOT ON EXCEPTION DISPLAY "arg" END-DISPLAY
           END-ACCEPT.
           STOP RUN.
