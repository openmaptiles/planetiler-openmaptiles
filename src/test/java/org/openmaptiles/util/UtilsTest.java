package org.openmaptiles.util;

import java.util.function.Function;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class UtilsTest {

  private record TestRecord(String value) {}

  private int counter2 = 0;
  private int counter3 = 0;
  private int counter4 = 0;
  private int counter5 = 0;
  private int counter6 = 0;

  @BeforeEach
  public void setup() {
    counter2 = 0;
    counter3 = 0;
    counter4 = 0;
    counter5 = 0;
    counter6 = 0;
  }

  @Test
  public void coalesceLazy2() {
    Function<TestRecord, Double> fb = (TestRecord s) -> {
      counter2++;
      return Double.valueOf(s.value());
    };

    TestRecord a = new TestRecord("1");
    TestRecord b = new TestRecord("2");

    Assertions.assertEquals(1, Utils.coalesceLazy(Double.valueOf(a.value()), fb, b));
    Assertions.assertEquals(0, counter2);

    Assertions.assertEquals(2, Utils.coalesceLazy(null, fb, b));
    Assertions.assertEquals(1, counter2);
  }

  @Test
  public void coalesceLazy3() {
    Function<TestRecord, Double> fb = (TestRecord s) -> {
      counter2++;
      return s == null ? null : Double.valueOf(s.value());
    };
    Function<TestRecord, Double> fc = (TestRecord s) -> {
      counter3++;
      return Double.valueOf(s.value());
    };

    TestRecord a = new TestRecord("1");
    TestRecord b = new TestRecord("2");
    TestRecord c = new TestRecord("3");

    Assertions.assertEquals(1, Utils.coalesceLazy(Double.valueOf(a.value()), fb, b, fc, c));
    Assertions.assertEquals(0, counter2);
    Assertions.assertEquals(0, counter3);

    Assertions.assertEquals(2, Utils.coalesceLazy(null, fb, b, fc, c));
    Assertions.assertEquals(1, counter2);
    Assertions.assertEquals(0, counter3);

    Assertions.assertEquals(3, Utils.coalesceLazy(null, fb, null, fc, c));
    Assertions.assertEquals(2, counter2);
    Assertions.assertEquals(1, counter3);
  }

  @Test
  public void coalesceLazy6() {
    Function<TestRecord, Double> fb = (TestRecord s) -> {
      counter2++;
      return s == null ? null : Double.valueOf(s.value());
    };
    Function<TestRecord, Double> fc = (TestRecord s) -> {
      counter3++;
      return s == null ? null : Double.valueOf(s.value());
    };
    Function<TestRecord, Double> fd = (TestRecord s) -> {
      counter4++;
      return s == null ? null : Double.valueOf(s.value());
    };
    Function<TestRecord, Double> fe = (TestRecord s) -> {
      counter5++;
      return s == null ? null : Double.valueOf(s.value());
    };
    Function<TestRecord, Double> ff = (TestRecord s) -> {
      counter6++;
      return s == null ? null : Double.valueOf(s.value());
    };

    TestRecord a = new TestRecord("1");
    TestRecord b = new TestRecord("2");
    TestRecord c = new TestRecord("3");
    TestRecord d = new TestRecord("4");
    TestRecord e = new TestRecord("5");
    TestRecord f = new TestRecord("6");

    Assertions.assertEquals(1, Utils.coalesceLazy(Double.valueOf(a.value()), fb, b, fc, c, fd, d, fe, e, ff, f));
    Assertions.assertEquals(0, counter2);
    Assertions.assertEquals(0, counter3);
    Assertions.assertEquals(0, counter4);
    Assertions.assertEquals(0, counter5);
    Assertions.assertEquals(0, counter6);

    Assertions.assertEquals(2, Utils.coalesceLazy(null, fb, b, fc, c, fd, d, fe, e, ff, f));
    Assertions.assertEquals(1, counter2);
    Assertions.assertEquals(0, counter3);
    Assertions.assertEquals(0, counter4);
    Assertions.assertEquals(0, counter5);
    Assertions.assertEquals(0, counter6);

    Assertions.assertEquals(3, Utils.coalesceLazy(null, fb, null, fc, c, fd, d, fe, e, ff, f));
    Assertions.assertEquals(2, counter2);
    Assertions.assertEquals(1, counter3);
    Assertions.assertEquals(0, counter4);
    Assertions.assertEquals(0, counter5);
    Assertions.assertEquals(0, counter6);

    Assertions.assertEquals(4, Utils.coalesceLazy(null, fb, null, fc, null, fd, d, fe, e, ff, f));
    Assertions.assertEquals(3, counter2);
    Assertions.assertEquals(2, counter3);
    Assertions.assertEquals(1, counter4);
    Assertions.assertEquals(0, counter5);
    Assertions.assertEquals(0, counter6);

    Assertions.assertEquals(5, Utils.coalesceLazy(null, fb, null, fc, null, fd, null, fe, e, ff, f));
    Assertions.assertEquals(4, counter2);
    Assertions.assertEquals(3, counter3);
    Assertions.assertEquals(2, counter4);
    Assertions.assertEquals(1, counter5);
    Assertions.assertEquals(0, counter6);

    Assertions.assertEquals(6, Utils.coalesceLazy(null, fb, null, fc, null, fd, null, fe, null, ff, f));
    Assertions.assertEquals(5, counter2);
    Assertions.assertEquals(4, counter3);
    Assertions.assertEquals(3, counter4);
    Assertions.assertEquals(2, counter5);
    Assertions.assertEquals(1, counter6);
  }
}
