01 RECORD-LAYOUT.
          05 FIELD-A         PIC X(10).
          05 FIELD-B         PIC 9(5).
          05 FIELD-C         PIC 9(3)V99.
          05 FIELD-D         REDEFINES FIELD-C.
             10 SUB-FIELD-D1 PIC X(2).
             10 SUB-FIELD-D2 PIC 9(3).
          05 FIELD-E         PIC X(1) OCCURS 3 TIMES.
