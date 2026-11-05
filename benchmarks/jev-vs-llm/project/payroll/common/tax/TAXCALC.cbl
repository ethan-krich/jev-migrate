       IDENTIFICATION DIVISION.
       PROGRAM-ID. TAXCALC.
      * Income tax withheld from one gross pay: 20% of the part above
      * the 500.00 allowance, rounded to the cent; none below it.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 WS-ALLOWANCE PIC 9(3)V99 VALUE 500.00.
       01 WS-RATE      PIC V99 VALUE .20.
       LINKAGE SECTION.
       01 LK-GROSS PIC 9(5)V99.
       01 LK-TAX   PIC 9(5)V99.
       PROCEDURE DIVISION USING LK-GROSS LK-TAX.
           IF LK-GROSS > WS-ALLOWANCE
               COMPUTE LK-TAX ROUNDED =
                   (LK-GROSS - WS-ALLOWANCE) * WS-RATE
           ELSE
               MOVE ZERO TO LK-TAX
           END-IF.
           GOBACK.
