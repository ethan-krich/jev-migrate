       IDENTIFICATION DIVISION.
       PROGRAM-ID. EDITED.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 E01 PIC ZZZ9.
       01 E02 PIC ZZZZ.
       01 E03 PIC 9999.
       01 E04 PIC Z,ZZ9.99.
       01 E05 PIC ZZ,ZZZ.ZZ.
       01 E06 PIC ***9.99.
       01 E07 PIC ****.**.
       01 E08 PIC $$$9.99.
       01 E09 PIC $$,$$9.99.
       01 E10 PIC $ZZ9.99.
       01 E11 PIC +++9.
       01 E12 PIC ---9.
       01 E13 PIC +ZZ9.
       01 E14 PIC -ZZ9.
       01 E15 PIC ZZ9+.
       01 E16 PIC ZZ9-.
       01 E17 PIC ZZ9.99CR.
       01 E18 PIC ZZ9.99DB.
       01 E19 PIC $***,**9.99-.
       01 E20 PIC 99B99B99.
       01 E21 PIC 99/99/99.
       01 E22 PIC 990099.
       01 E23 PIC +999.99.
       01 E24 PIC $---9.99.
       01 E25 PIC $$$9.99CR.
       01 E26 PIC ZZZ.99.
       01 E27 PIC .9999.
       01 E28 PIC $$$$.
       01 E29 PIC ++++.
       01 E30 PIC Z(4)9.
       01 E31 PIC 9(3).9(2)-.
       01 E32 PIC *(5).99.
       01 E33 PIC +(5).
       01 E34 PIC -(4).99.
       01 E35 PIC ZZBZZ9.
       01 BW  PIC 9(3) BLANK WHEN ZERO.
       01 BWE PIC ZZ9.99 BLANK WHEN ZERO.
       01 AE  PIC XXBXX/XX0X.
       01 AL  PIC A(4).
       01 V   PIC S9(5)V9(3).
       01 D   PIC S9(5)V99.
       01 X10 PIC X(10).
       01 IV  PIC ZZ9.99 VALUE 3.5.
       01 IU  PIC ZZ9.99.
       PROCEDURE DIVISION.
           DISPLAY "[" IV "][" IU "]".
           MOVE 0 TO V.
           DISPLAY "value 0".
           MOVE V TO E01.
           DISPLAY "  ZZZ9          [" E01 "]".
           MOVE V TO E02.
           DISPLAY "  ZZZZ          [" E02 "]".
           MOVE V TO E03.
           DISPLAY "  9999          [" E03 "]".
           MOVE V TO E04.
           DISPLAY "  Z,ZZ9.99      [" E04 "]".
           MOVE V TO E05.
           DISPLAY "  ZZ,ZZZ.ZZ     [" E05 "]".
           MOVE V TO E06.
           DISPLAY "  ***9.99       [" E06 "]".
           MOVE V TO E07.
           DISPLAY "  ****.**       [" E07 "]".
           MOVE V TO E08.
           DISPLAY "  $$$9.99       [" E08 "]".
           MOVE V TO E09.
           DISPLAY "  $$,$$9.99     [" E09 "]".
           MOVE V TO E10.
           DISPLAY "  $ZZ9.99       [" E10 "]".
           MOVE V TO E11.
           DISPLAY "  +++9          [" E11 "]".
           MOVE V TO E12.
           DISPLAY "  ---9          [" E12 "]".
           MOVE V TO E13.
           DISPLAY "  +ZZ9          [" E13 "]".
           MOVE V TO E14.
           DISPLAY "  -ZZ9          [" E14 "]".
           MOVE V TO E15.
           DISPLAY "  ZZ9+          [" E15 "]".
           MOVE V TO E16.
           DISPLAY "  ZZ9-          [" E16 "]".
           MOVE V TO E17.
           DISPLAY "  ZZ9.99CR      [" E17 "]".
           MOVE V TO E18.
           DISPLAY "  ZZ9.99DB      [" E18 "]".
           MOVE V TO E19.
           DISPLAY "  $***,**9.99-  [" E19 "]".
           MOVE V TO E20.
           DISPLAY "  99B99B99      [" E20 "]".
           MOVE V TO E21.
           DISPLAY "  99/99/99      [" E21 "]".
           MOVE V TO E22.
           DISPLAY "  990099        [" E22 "]".
           MOVE V TO E23.
           DISPLAY "  +999.99       [" E23 "]".
           MOVE V TO E24.
           DISPLAY "  $---9.99      [" E24 "]".
           MOVE V TO E25.
           DISPLAY "  $$$9.99CR     [" E25 "]".
           MOVE V TO E26.
           DISPLAY "  ZZZ.99        [" E26 "]".
           MOVE V TO E27.
           DISPLAY "  .9999         [" E27 "]".
           MOVE V TO E28.
           DISPLAY "  $$$$          [" E28 "]".
           MOVE V TO E29.
           DISPLAY "  ++++          [" E29 "]".
           MOVE V TO E30.
           DISPLAY "  Z(4)9         [" E30 "]".
           MOVE V TO E31.
           DISPLAY "  9(3).9(2)-    [" E31 "]".
           MOVE V TO E32.
           DISPLAY "  *(5).99       [" E32 "]".
           MOVE V TO E33.
           DISPLAY "  +(5)          [" E33 "]".
           MOVE V TO E34.
           DISPLAY "  -(4).99       [" E34 "]".
           MOVE V TO E35.
           DISPLAY "  ZZBZZ9        [" E35 "]".
           MOVE V TO BW BWE.
           DISPLAY "  BWZ [" BW "][" BWE "]".
           MOVE 5 TO V.
           DISPLAY "value 5".
           MOVE V TO E01.
           DISPLAY "  ZZZ9          [" E01 "]".
           MOVE V TO E02.
           DISPLAY "  ZZZZ          [" E02 "]".
           MOVE V TO E03.
           DISPLAY "  9999          [" E03 "]".
           MOVE V TO E04.
           DISPLAY "  Z,ZZ9.99      [" E04 "]".
           MOVE V TO E05.
           DISPLAY "  ZZ,ZZZ.ZZ     [" E05 "]".
           MOVE V TO E06.
           DISPLAY "  ***9.99       [" E06 "]".
           MOVE V TO E07.
           DISPLAY "  ****.**       [" E07 "]".
           MOVE V TO E08.
           DISPLAY "  $$$9.99       [" E08 "]".
           MOVE V TO E09.
           DISPLAY "  $$,$$9.99     [" E09 "]".
           MOVE V TO E10.
           DISPLAY "  $ZZ9.99       [" E10 "]".
           MOVE V TO E11.
           DISPLAY "  +++9          [" E11 "]".
           MOVE V TO E12.
           DISPLAY "  ---9          [" E12 "]".
           MOVE V TO E13.
           DISPLAY "  +ZZ9          [" E13 "]".
           MOVE V TO E14.
           DISPLAY "  -ZZ9          [" E14 "]".
           MOVE V TO E15.
           DISPLAY "  ZZ9+          [" E15 "]".
           MOVE V TO E16.
           DISPLAY "  ZZ9-          [" E16 "]".
           MOVE V TO E17.
           DISPLAY "  ZZ9.99CR      [" E17 "]".
           MOVE V TO E18.
           DISPLAY "  ZZ9.99DB      [" E18 "]".
           MOVE V TO E19.
           DISPLAY "  $***,**9.99-  [" E19 "]".
           MOVE V TO E20.
           DISPLAY "  99B99B99      [" E20 "]".
           MOVE V TO E21.
           DISPLAY "  99/99/99      [" E21 "]".
           MOVE V TO E22.
           DISPLAY "  990099        [" E22 "]".
           MOVE V TO E23.
           DISPLAY "  +999.99       [" E23 "]".
           MOVE V TO E24.
           DISPLAY "  $---9.99      [" E24 "]".
           MOVE V TO E25.
           DISPLAY "  $$$9.99CR     [" E25 "]".
           MOVE V TO E26.
           DISPLAY "  ZZZ.99        [" E26 "]".
           MOVE V TO E27.
           DISPLAY "  .9999         [" E27 "]".
           MOVE V TO E28.
           DISPLAY "  $$$$          [" E28 "]".
           MOVE V TO E29.
           DISPLAY "  ++++          [" E29 "]".
           MOVE V TO E30.
           DISPLAY "  Z(4)9         [" E30 "]".
           MOVE V TO E31.
           DISPLAY "  9(3).9(2)-    [" E31 "]".
           MOVE V TO E32.
           DISPLAY "  *(5).99       [" E32 "]".
           MOVE V TO E33.
           DISPLAY "  +(5)          [" E33 "]".
           MOVE V TO E34.
           DISPLAY "  -(4).99       [" E34 "]".
           MOVE V TO E35.
           DISPLAY "  ZZBZZ9        [" E35 "]".
           MOVE V TO BW BWE.
           DISPLAY "  BWZ [" BW "][" BWE "]".
           MOVE -5 TO V.
           DISPLAY "value -5".
           MOVE V TO E01.
           DISPLAY "  ZZZ9          [" E01 "]".
           MOVE V TO E02.
           DISPLAY "  ZZZZ          [" E02 "]".
           MOVE V TO E03.
           DISPLAY "  9999          [" E03 "]".
           MOVE V TO E04.
           DISPLAY "  Z,ZZ9.99      [" E04 "]".
           MOVE V TO E05.
           DISPLAY "  ZZ,ZZZ.ZZ     [" E05 "]".
           MOVE V TO E06.
           DISPLAY "  ***9.99       [" E06 "]".
           MOVE V TO E07.
           DISPLAY "  ****.**       [" E07 "]".
           MOVE V TO E08.
           DISPLAY "  $$$9.99       [" E08 "]".
           MOVE V TO E09.
           DISPLAY "  $$,$$9.99     [" E09 "]".
           MOVE V TO E10.
           DISPLAY "  $ZZ9.99       [" E10 "]".
           MOVE V TO E11.
           DISPLAY "  +++9          [" E11 "]".
           MOVE V TO E12.
           DISPLAY "  ---9          [" E12 "]".
           MOVE V TO E13.
           DISPLAY "  +ZZ9          [" E13 "]".
           MOVE V TO E14.
           DISPLAY "  -ZZ9          [" E14 "]".
           MOVE V TO E15.
           DISPLAY "  ZZ9+          [" E15 "]".
           MOVE V TO E16.
           DISPLAY "  ZZ9-          [" E16 "]".
           MOVE V TO E17.
           DISPLAY "  ZZ9.99CR      [" E17 "]".
           MOVE V TO E18.
           DISPLAY "  ZZ9.99DB      [" E18 "]".
           MOVE V TO E19.
           DISPLAY "  $***,**9.99-  [" E19 "]".
           MOVE V TO E20.
           DISPLAY "  99B99B99      [" E20 "]".
           MOVE V TO E21.
           DISPLAY "  99/99/99      [" E21 "]".
           MOVE V TO E22.
           DISPLAY "  990099        [" E22 "]".
           MOVE V TO E23.
           DISPLAY "  +999.99       [" E23 "]".
           MOVE V TO E24.
           DISPLAY "  $---9.99      [" E24 "]".
           MOVE V TO E25.
           DISPLAY "  $$$9.99CR     [" E25 "]".
           MOVE V TO E26.
           DISPLAY "  ZZZ.99        [" E26 "]".
           MOVE V TO E27.
           DISPLAY "  .9999         [" E27 "]".
           MOVE V TO E28.
           DISPLAY "  $$$$          [" E28 "]".
           MOVE V TO E29.
           DISPLAY "  ++++          [" E29 "]".
           MOVE V TO E30.
           DISPLAY "  Z(4)9         [" E30 "]".
           MOVE V TO E31.
           DISPLAY "  9(3).9(2)-    [" E31 "]".
           MOVE V TO E32.
           DISPLAY "  *(5).99       [" E32 "]".
           MOVE V TO E33.
           DISPLAY "  +(5)          [" E33 "]".
           MOVE V TO E34.
           DISPLAY "  -(4).99       [" E34 "]".
           MOVE V TO E35.
           DISPLAY "  ZZBZZ9        [" E35 "]".
           MOVE V TO BW BWE.
           DISPLAY "  BWZ [" BW "][" BWE "]".
           MOVE 12.34 TO V.
           DISPLAY "value 12.34".
           MOVE V TO E01.
           DISPLAY "  ZZZ9          [" E01 "]".
           MOVE V TO E02.
           DISPLAY "  ZZZZ          [" E02 "]".
           MOVE V TO E03.
           DISPLAY "  9999          [" E03 "]".
           MOVE V TO E04.
           DISPLAY "  Z,ZZ9.99      [" E04 "]".
           MOVE V TO E05.
           DISPLAY "  ZZ,ZZZ.ZZ     [" E05 "]".
           MOVE V TO E06.
           DISPLAY "  ***9.99       [" E06 "]".
           MOVE V TO E07.
           DISPLAY "  ****.**       [" E07 "]".
           MOVE V TO E08.
           DISPLAY "  $$$9.99       [" E08 "]".
           MOVE V TO E09.
           DISPLAY "  $$,$$9.99     [" E09 "]".
           MOVE V TO E10.
           DISPLAY "  $ZZ9.99       [" E10 "]".
           MOVE V TO E11.
           DISPLAY "  +++9          [" E11 "]".
           MOVE V TO E12.
           DISPLAY "  ---9          [" E12 "]".
           MOVE V TO E13.
           DISPLAY "  +ZZ9          [" E13 "]".
           MOVE V TO E14.
           DISPLAY "  -ZZ9          [" E14 "]".
           MOVE V TO E15.
           DISPLAY "  ZZ9+          [" E15 "]".
           MOVE V TO E16.
           DISPLAY "  ZZ9-          [" E16 "]".
           MOVE V TO E17.
           DISPLAY "  ZZ9.99CR      [" E17 "]".
           MOVE V TO E18.
           DISPLAY "  ZZ9.99DB      [" E18 "]".
           MOVE V TO E19.
           DISPLAY "  $***,**9.99-  [" E19 "]".
           MOVE V TO E20.
           DISPLAY "  99B99B99      [" E20 "]".
           MOVE V TO E21.
           DISPLAY "  99/99/99      [" E21 "]".
           MOVE V TO E22.
           DISPLAY "  990099        [" E22 "]".
           MOVE V TO E23.
           DISPLAY "  +999.99       [" E23 "]".
           MOVE V TO E24.
           DISPLAY "  $---9.99      [" E24 "]".
           MOVE V TO E25.
           DISPLAY "  $$$9.99CR     [" E25 "]".
           MOVE V TO E26.
           DISPLAY "  ZZZ.99        [" E26 "]".
           MOVE V TO E27.
           DISPLAY "  .9999         [" E27 "]".
           MOVE V TO E28.
           DISPLAY "  $$$$          [" E28 "]".
           MOVE V TO E29.
           DISPLAY "  ++++          [" E29 "]".
           MOVE V TO E30.
           DISPLAY "  Z(4)9         [" E30 "]".
           MOVE V TO E31.
           DISPLAY "  9(3).9(2)-    [" E31 "]".
           MOVE V TO E32.
           DISPLAY "  *(5).99       [" E32 "]".
           MOVE V TO E33.
           DISPLAY "  +(5)          [" E33 "]".
           MOVE V TO E34.
           DISPLAY "  -(4).99       [" E34 "]".
           MOVE V TO E35.
           DISPLAY "  ZZBZZ9        [" E35 "]".
           MOVE V TO BW BWE.
           DISPLAY "  BWZ [" BW "][" BWE "]".
           MOVE -12.34 TO V.
           DISPLAY "value -12.34".
           MOVE V TO E01.
           DISPLAY "  ZZZ9          [" E01 "]".
           MOVE V TO E02.
           DISPLAY "  ZZZZ          [" E02 "]".
           MOVE V TO E03.
           DISPLAY "  9999          [" E03 "]".
           MOVE V TO E04.
           DISPLAY "  Z,ZZ9.99      [" E04 "]".
           MOVE V TO E05.
           DISPLAY "  ZZ,ZZZ.ZZ     [" E05 "]".
           MOVE V TO E06.
           DISPLAY "  ***9.99       [" E06 "]".
           MOVE V TO E07.
           DISPLAY "  ****.**       [" E07 "]".
           MOVE V TO E08.
           DISPLAY "  $$$9.99       [" E08 "]".
           MOVE V TO E09.
           DISPLAY "  $$,$$9.99     [" E09 "]".
           MOVE V TO E10.
           DISPLAY "  $ZZ9.99       [" E10 "]".
           MOVE V TO E11.
           DISPLAY "  +++9          [" E11 "]".
           MOVE V TO E12.
           DISPLAY "  ---9          [" E12 "]".
           MOVE V TO E13.
           DISPLAY "  +ZZ9          [" E13 "]".
           MOVE V TO E14.
           DISPLAY "  -ZZ9          [" E14 "]".
           MOVE V TO E15.
           DISPLAY "  ZZ9+          [" E15 "]".
           MOVE V TO E16.
           DISPLAY "  ZZ9-          [" E16 "]".
           MOVE V TO E17.
           DISPLAY "  ZZ9.99CR      [" E17 "]".
           MOVE V TO E18.
           DISPLAY "  ZZ9.99DB      [" E18 "]".
           MOVE V TO E19.
           DISPLAY "  $***,**9.99-  [" E19 "]".
           MOVE V TO E20.
           DISPLAY "  99B99B99      [" E20 "]".
           MOVE V TO E21.
           DISPLAY "  99/99/99      [" E21 "]".
           MOVE V TO E22.
           DISPLAY "  990099        [" E22 "]".
           MOVE V TO E23.
           DISPLAY "  +999.99       [" E23 "]".
           MOVE V TO E24.
           DISPLAY "  $---9.99      [" E24 "]".
           MOVE V TO E25.
           DISPLAY "  $$$9.99CR     [" E25 "]".
           MOVE V TO E26.
           DISPLAY "  ZZZ.99        [" E26 "]".
           MOVE V TO E27.
           DISPLAY "  .9999         [" E27 "]".
           MOVE V TO E28.
           DISPLAY "  $$$$          [" E28 "]".
           MOVE V TO E29.
           DISPLAY "  ++++          [" E29 "]".
           MOVE V TO E30.
           DISPLAY "  Z(4)9         [" E30 "]".
           MOVE V TO E31.
           DISPLAY "  9(3).9(2)-    [" E31 "]".
           MOVE V TO E32.
           DISPLAY "  *(5).99       [" E32 "]".
           MOVE V TO E33.
           DISPLAY "  +(5)          [" E33 "]".
           MOVE V TO E34.
           DISPLAY "  -(4).99       [" E34 "]".
           MOVE V TO E35.
           DISPLAY "  ZZBZZ9        [" E35 "]".
           MOVE V TO BW BWE.
           DISPLAY "  BWZ [" BW "][" BWE "]".
           MOVE 1234.5 TO V.
           DISPLAY "value 1234.5".
           MOVE V TO E01.
           DISPLAY "  ZZZ9          [" E01 "]".
           MOVE V TO E02.
           DISPLAY "  ZZZZ          [" E02 "]".
           MOVE V TO E03.
           DISPLAY "  9999          [" E03 "]".
           MOVE V TO E04.
           DISPLAY "  Z,ZZ9.99      [" E04 "]".
           MOVE V TO E05.
           DISPLAY "  ZZ,ZZZ.ZZ     [" E05 "]".
           MOVE V TO E06.
           DISPLAY "  ***9.99       [" E06 "]".
           MOVE V TO E07.
           DISPLAY "  ****.**       [" E07 "]".
           MOVE V TO E08.
           DISPLAY "  $$$9.99       [" E08 "]".
           MOVE V TO E09.
           DISPLAY "  $$,$$9.99     [" E09 "]".
           MOVE V TO E10.
           DISPLAY "  $ZZ9.99       [" E10 "]".
           MOVE V TO E11.
           DISPLAY "  +++9          [" E11 "]".
           MOVE V TO E12.
           DISPLAY "  ---9          [" E12 "]".
           MOVE V TO E13.
           DISPLAY "  +ZZ9          [" E13 "]".
           MOVE V TO E14.
           DISPLAY "  -ZZ9          [" E14 "]".
           MOVE V TO E15.
           DISPLAY "  ZZ9+          [" E15 "]".
           MOVE V TO E16.
           DISPLAY "  ZZ9-          [" E16 "]".
           MOVE V TO E17.
           DISPLAY "  ZZ9.99CR      [" E17 "]".
           MOVE V TO E18.
           DISPLAY "  ZZ9.99DB      [" E18 "]".
           MOVE V TO E19.
           DISPLAY "  $***,**9.99-  [" E19 "]".
           MOVE V TO E20.
           DISPLAY "  99B99B99      [" E20 "]".
           MOVE V TO E21.
           DISPLAY "  99/99/99      [" E21 "]".
           MOVE V TO E22.
           DISPLAY "  990099        [" E22 "]".
           MOVE V TO E23.
           DISPLAY "  +999.99       [" E23 "]".
           MOVE V TO E24.
           DISPLAY "  $---9.99      [" E24 "]".
           MOVE V TO E25.
           DISPLAY "  $$$9.99CR     [" E25 "]".
           MOVE V TO E26.
           DISPLAY "  ZZZ.99        [" E26 "]".
           MOVE V TO E27.
           DISPLAY "  .9999         [" E27 "]".
           MOVE V TO E28.
           DISPLAY "  $$$$          [" E28 "]".
           MOVE V TO E29.
           DISPLAY "  ++++          [" E29 "]".
           MOVE V TO E30.
           DISPLAY "  Z(4)9         [" E30 "]".
           MOVE V TO E31.
           DISPLAY "  9(3).9(2)-    [" E31 "]".
           MOVE V TO E32.
           DISPLAY "  *(5).99       [" E32 "]".
           MOVE V TO E33.
           DISPLAY "  +(5)          [" E33 "]".
           MOVE V TO E34.
           DISPLAY "  -(4).99       [" E34 "]".
           MOVE V TO E35.
           DISPLAY "  ZZBZZ9        [" E35 "]".
           MOVE V TO BW BWE.
           DISPLAY "  BWZ [" BW "][" BWE "]".
           MOVE -1234.5 TO V.
           DISPLAY "value -1234.5".
           MOVE V TO E01.
           DISPLAY "  ZZZ9          [" E01 "]".
           MOVE V TO E02.
           DISPLAY "  ZZZZ          [" E02 "]".
           MOVE V TO E03.
           DISPLAY "  9999          [" E03 "]".
           MOVE V TO E04.
           DISPLAY "  Z,ZZ9.99      [" E04 "]".
           MOVE V TO E05.
           DISPLAY "  ZZ,ZZZ.ZZ     [" E05 "]".
           MOVE V TO E06.
           DISPLAY "  ***9.99       [" E06 "]".
           MOVE V TO E07.
           DISPLAY "  ****.**       [" E07 "]".
           MOVE V TO E08.
           DISPLAY "  $$$9.99       [" E08 "]".
           MOVE V TO E09.
           DISPLAY "  $$,$$9.99     [" E09 "]".
           MOVE V TO E10.
           DISPLAY "  $ZZ9.99       [" E10 "]".
           MOVE V TO E11.
           DISPLAY "  +++9          [" E11 "]".
           MOVE V TO E12.
           DISPLAY "  ---9          [" E12 "]".
           MOVE V TO E13.
           DISPLAY "  +ZZ9          [" E13 "]".
           MOVE V TO E14.
           DISPLAY "  -ZZ9          [" E14 "]".
           MOVE V TO E15.
           DISPLAY "  ZZ9+          [" E15 "]".
           MOVE V TO E16.
           DISPLAY "  ZZ9-          [" E16 "]".
           MOVE V TO E17.
           DISPLAY "  ZZ9.99CR      [" E17 "]".
           MOVE V TO E18.
           DISPLAY "  ZZ9.99DB      [" E18 "]".
           MOVE V TO E19.
           DISPLAY "  $***,**9.99-  [" E19 "]".
           MOVE V TO E20.
           DISPLAY "  99B99B99      [" E20 "]".
           MOVE V TO E21.
           DISPLAY "  99/99/99      [" E21 "]".
           MOVE V TO E22.
           DISPLAY "  990099        [" E22 "]".
           MOVE V TO E23.
           DISPLAY "  +999.99       [" E23 "]".
           MOVE V TO E24.
           DISPLAY "  $---9.99      [" E24 "]".
           MOVE V TO E25.
           DISPLAY "  $$$9.99CR     [" E25 "]".
           MOVE V TO E26.
           DISPLAY "  ZZZ.99        [" E26 "]".
           MOVE V TO E27.
           DISPLAY "  .9999         [" E27 "]".
           MOVE V TO E28.
           DISPLAY "  $$$$          [" E28 "]".
           MOVE V TO E29.
           DISPLAY "  ++++          [" E29 "]".
           MOVE V TO E30.
           DISPLAY "  Z(4)9         [" E30 "]".
           MOVE V TO E31.
           DISPLAY "  9(3).9(2)-    [" E31 "]".
           MOVE V TO E32.
           DISPLAY "  *(5).99       [" E32 "]".
           MOVE V TO E33.
           DISPLAY "  +(5)          [" E33 "]".
           MOVE V TO E34.
           DISPLAY "  -(4).99       [" E34 "]".
           MOVE V TO E35.
           DISPLAY "  ZZBZZ9        [" E35 "]".
           MOVE V TO BW BWE.
           DISPLAY "  BWZ [" BW "][" BWE "]".
           MOVE 0.07 TO V.
           DISPLAY "value 0.07".
           MOVE V TO E01.
           DISPLAY "  ZZZ9          [" E01 "]".
           MOVE V TO E02.
           DISPLAY "  ZZZZ          [" E02 "]".
           MOVE V TO E03.
           DISPLAY "  9999          [" E03 "]".
           MOVE V TO E04.
           DISPLAY "  Z,ZZ9.99      [" E04 "]".
           MOVE V TO E05.
           DISPLAY "  ZZ,ZZZ.ZZ     [" E05 "]".
           MOVE V TO E06.
           DISPLAY "  ***9.99       [" E06 "]".
           MOVE V TO E07.
           DISPLAY "  ****.**       [" E07 "]".
           MOVE V TO E08.
           DISPLAY "  $$$9.99       [" E08 "]".
           MOVE V TO E09.
           DISPLAY "  $$,$$9.99     [" E09 "]".
           MOVE V TO E10.
           DISPLAY "  $ZZ9.99       [" E10 "]".
           MOVE V TO E11.
           DISPLAY "  +++9          [" E11 "]".
           MOVE V TO E12.
           DISPLAY "  ---9          [" E12 "]".
           MOVE V TO E13.
           DISPLAY "  +ZZ9          [" E13 "]".
           MOVE V TO E14.
           DISPLAY "  -ZZ9          [" E14 "]".
           MOVE V TO E15.
           DISPLAY "  ZZ9+          [" E15 "]".
           MOVE V TO E16.
           DISPLAY "  ZZ9-          [" E16 "]".
           MOVE V TO E17.
           DISPLAY "  ZZ9.99CR      [" E17 "]".
           MOVE V TO E18.
           DISPLAY "  ZZ9.99DB      [" E18 "]".
           MOVE V TO E19.
           DISPLAY "  $***,**9.99-  [" E19 "]".
           MOVE V TO E20.
           DISPLAY "  99B99B99      [" E20 "]".
           MOVE V TO E21.
           DISPLAY "  99/99/99      [" E21 "]".
           MOVE V TO E22.
           DISPLAY "  990099        [" E22 "]".
           MOVE V TO E23.
           DISPLAY "  +999.99       [" E23 "]".
           MOVE V TO E24.
           DISPLAY "  $---9.99      [" E24 "]".
           MOVE V TO E25.
           DISPLAY "  $$$9.99CR     [" E25 "]".
           MOVE V TO E26.
           DISPLAY "  ZZZ.99        [" E26 "]".
           MOVE V TO E27.
           DISPLAY "  .9999         [" E27 "]".
           MOVE V TO E28.
           DISPLAY "  $$$$          [" E28 "]".
           MOVE V TO E29.
           DISPLAY "  ++++          [" E29 "]".
           MOVE V TO E30.
           DISPLAY "  Z(4)9         [" E30 "]".
           MOVE V TO E31.
           DISPLAY "  9(3).9(2)-    [" E31 "]".
           MOVE V TO E32.
           DISPLAY "  *(5).99       [" E32 "]".
           MOVE V TO E33.
           DISPLAY "  +(5)          [" E33 "]".
           MOVE V TO E34.
           DISPLAY "  -(4).99       [" E34 "]".
           MOVE V TO E35.
           DISPLAY "  ZZBZZ9        [" E35 "]".
           MOVE V TO BW BWE.
           DISPLAY "  BWZ [" BW "][" BWE "]".
           MOVE -0.07 TO V.
           DISPLAY "value -0.07".
           MOVE V TO E01.
           DISPLAY "  ZZZ9          [" E01 "]".
           MOVE V TO E02.
           DISPLAY "  ZZZZ          [" E02 "]".
           MOVE V TO E03.
           DISPLAY "  9999          [" E03 "]".
           MOVE V TO E04.
           DISPLAY "  Z,ZZ9.99      [" E04 "]".
           MOVE V TO E05.
           DISPLAY "  ZZ,ZZZ.ZZ     [" E05 "]".
           MOVE V TO E06.
           DISPLAY "  ***9.99       [" E06 "]".
           MOVE V TO E07.
           DISPLAY "  ****.**       [" E07 "]".
           MOVE V TO E08.
           DISPLAY "  $$$9.99       [" E08 "]".
           MOVE V TO E09.
           DISPLAY "  $$,$$9.99     [" E09 "]".
           MOVE V TO E10.
           DISPLAY "  $ZZ9.99       [" E10 "]".
           MOVE V TO E11.
           DISPLAY "  +++9          [" E11 "]".
           MOVE V TO E12.
           DISPLAY "  ---9          [" E12 "]".
           MOVE V TO E13.
           DISPLAY "  +ZZ9          [" E13 "]".
           MOVE V TO E14.
           DISPLAY "  -ZZ9          [" E14 "]".
           MOVE V TO E15.
           DISPLAY "  ZZ9+          [" E15 "]".
           MOVE V TO E16.
           DISPLAY "  ZZ9-          [" E16 "]".
           MOVE V TO E17.
           DISPLAY "  ZZ9.99CR      [" E17 "]".
           MOVE V TO E18.
           DISPLAY "  ZZ9.99DB      [" E18 "]".
           MOVE V TO E19.
           DISPLAY "  $***,**9.99-  [" E19 "]".
           MOVE V TO E20.
           DISPLAY "  99B99B99      [" E20 "]".
           MOVE V TO E21.
           DISPLAY "  99/99/99      [" E21 "]".
           MOVE V TO E22.
           DISPLAY "  990099        [" E22 "]".
           MOVE V TO E23.
           DISPLAY "  +999.99       [" E23 "]".
           MOVE V TO E24.
           DISPLAY "  $---9.99      [" E24 "]".
           MOVE V TO E25.
           DISPLAY "  $$$9.99CR     [" E25 "]".
           MOVE V TO E26.
           DISPLAY "  ZZZ.99        [" E26 "]".
           MOVE V TO E27.
           DISPLAY "  .9999         [" E27 "]".
           MOVE V TO E28.
           DISPLAY "  $$$$          [" E28 "]".
           MOVE V TO E29.
           DISPLAY "  ++++          [" E29 "]".
           MOVE V TO E30.
           DISPLAY "  Z(4)9         [" E30 "]".
           MOVE V TO E31.
           DISPLAY "  9(3).9(2)-    [" E31 "]".
           MOVE V TO E32.
           DISPLAY "  *(5).99       [" E32 "]".
           MOVE V TO E33.
           DISPLAY "  +(5)          [" E33 "]".
           MOVE V TO E34.
           DISPLAY "  -(4).99       [" E34 "]".
           MOVE V TO E35.
           DISPLAY "  ZZBZZ9        [" E35 "]".
           MOVE V TO BW BWE.
           DISPLAY "  BWZ [" BW "][" BWE "]".
           MOVE 98765.432 TO V.
           DISPLAY "value 98765.432".
           MOVE V TO E01.
           DISPLAY "  ZZZ9          [" E01 "]".
           MOVE V TO E02.
           DISPLAY "  ZZZZ          [" E02 "]".
           MOVE V TO E03.
           DISPLAY "  9999          [" E03 "]".
           MOVE V TO E04.
           DISPLAY "  Z,ZZ9.99      [" E04 "]".
           MOVE V TO E05.
           DISPLAY "  ZZ,ZZZ.ZZ     [" E05 "]".
           MOVE V TO E06.
           DISPLAY "  ***9.99       [" E06 "]".
           MOVE V TO E07.
           DISPLAY "  ****.**       [" E07 "]".
           MOVE V TO E08.
           DISPLAY "  $$$9.99       [" E08 "]".
           MOVE V TO E09.
           DISPLAY "  $$,$$9.99     [" E09 "]".
           MOVE V TO E10.
           DISPLAY "  $ZZ9.99       [" E10 "]".
           MOVE V TO E11.
           DISPLAY "  +++9          [" E11 "]".
           MOVE V TO E12.
           DISPLAY "  ---9          [" E12 "]".
           MOVE V TO E13.
           DISPLAY "  +ZZ9          [" E13 "]".
           MOVE V TO E14.
           DISPLAY "  -ZZ9          [" E14 "]".
           MOVE V TO E15.
           DISPLAY "  ZZ9+          [" E15 "]".
           MOVE V TO E16.
           DISPLAY "  ZZ9-          [" E16 "]".
           MOVE V TO E17.
           DISPLAY "  ZZ9.99CR      [" E17 "]".
           MOVE V TO E18.
           DISPLAY "  ZZ9.99DB      [" E18 "]".
           MOVE V TO E19.
           DISPLAY "  $***,**9.99-  [" E19 "]".
           MOVE V TO E20.
           DISPLAY "  99B99B99      [" E20 "]".
           MOVE V TO E21.
           DISPLAY "  99/99/99      [" E21 "]".
           MOVE V TO E22.
           DISPLAY "  990099        [" E22 "]".
           MOVE V TO E23.
           DISPLAY "  +999.99       [" E23 "]".
           MOVE V TO E24.
           DISPLAY "  $---9.99      [" E24 "]".
           MOVE V TO E25.
           DISPLAY "  $$$9.99CR     [" E25 "]".
           MOVE V TO E26.
           DISPLAY "  ZZZ.99        [" E26 "]".
           MOVE V TO E27.
           DISPLAY "  .9999         [" E27 "]".
           MOVE V TO E28.
           DISPLAY "  $$$$          [" E28 "]".
           MOVE V TO E29.
           DISPLAY "  ++++          [" E29 "]".
           MOVE V TO E30.
           DISPLAY "  Z(4)9         [" E30 "]".
           MOVE V TO E31.
           DISPLAY "  9(3).9(2)-    [" E31 "]".
           MOVE V TO E32.
           DISPLAY "  *(5).99       [" E32 "]".
           MOVE V TO E33.
           DISPLAY "  +(5)          [" E33 "]".
           MOVE V TO E34.
           DISPLAY "  -(4).99       [" E34 "]".
           MOVE V TO E35.
           DISPLAY "  ZZBZZ9        [" E35 "]".
           MOVE V TO BW BWE.
           DISPLAY "  BWZ [" BW "][" BWE "]".
           MOVE "abcdefgh" TO AE. DISPLAY "[" AE "]".
           MOVE "ab" TO AE. DISPLAY "[" AE "]".
           MOVE "xy" TO AL. DISPLAY "[" AL "]".
           MOVE -1234.5 TO E04.
           MOVE E04 TO D. DISPLAY D.
           MOVE E19 TO D. DISPLAY D.
           MOVE -0.07 TO E17. MOVE E17 TO D. DISPLAY D.
           MOVE E04 TO X10. DISPLAY "[" X10 "]".
           MOVE ZERO TO E01 E06. DISPLAY "[" E01 "][" E06 "]".
           MOVE 0 TO E02. DISPLAY "[" E02 "]".
           IF E02 = SPACES DISPLAY "spaces".
           STOP RUN.
