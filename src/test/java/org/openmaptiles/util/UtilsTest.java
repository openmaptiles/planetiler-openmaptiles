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
  public void coalesce2L() {
    Function<TestRecord, Double> r2d = (TestRecord s) -> {
      counter2++;
      return Double.valueOf(s.value());
    };

    TestRecord a = new TestRecord("1");
    TestRecord b = new TestRecord("2");

    Assertions.assertEquals(1, Utils.coalesceF(Double.valueOf(a.value()), r2d, b));
    Assertions.assertEquals(0, counter2);

    Assertions.assertEquals(2, Utils.coalesceF(null, r2d, b));
    Assertions.assertEquals(1, counter2);
  }
}
