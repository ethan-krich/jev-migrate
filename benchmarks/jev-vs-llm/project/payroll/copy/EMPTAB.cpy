      * The employees paid this week: id, name, hours worked (one
      * decimal) and hourly rate (cents).
       01 EMPLOYEE-DATA.
          05 FILLER PIC X(21) VALUE "E001ALICE     4002500".
          05 FILLER PIC X(21) VALUE "E002BOB       3751840".
          05 FILLER PIC X(21) VALUE "E003CAROL     4553115".
          05 FILLER PIC X(21) VALUE "E004MARY-JO   1201575".
       01 EMPLOYEE-TABLE REDEFINES EMPLOYEE-DATA.
          05 EMPLOYEE OCCURS 4 TIMES.
             10 EMP-ID    PIC X(4).
             10 EMP-NAME  PIC X(10).
             10 EMP-HOURS PIC 9(2)V9.
             10 EMP-RATE  PIC 9(2)V99.
