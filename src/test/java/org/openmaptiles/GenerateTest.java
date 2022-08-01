package org.openmaptiles;

import static com.onthegomap.planetiler.expression.Expression.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.fasterxml.jackson.databind.JsonNode;
import com.onthegomap.planetiler.expression.Expression;
import com.onthegomap.planetiler.expression.MultiExpression;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class GenerateTest {

  @Test
  void testParseSimple() {
    MultiExpression<String> parsed = Generate.generateFieldMapping(Generate.parseYaml("""
      output:
        key: value
        key2:
          - value2
          - '%value3%'
      """));
    assertEquals(MultiExpression.of(List.of(
      MultiExpression.entry("output", or(
        matchAny("key", "value"),
        matchAny("key2", "value2", "%value3%")
      ))
    )), parsed);
  }

  @Test
  void testParseAnd() {
    MultiExpression<String> parsed = Generate.generateFieldMapping(Generate.parseYaml("""
      output:
        __AND__:
          key1: val1
          key2: val2
      """));
    assertEquals(MultiExpression.of(List.of(
      MultiExpression.entry("output", and(
        matchAny("key1", "val1"),
        matchAny("key2", "val2")
      ))
    )), parsed);
  }

  @Test
  void testParseAndWithOthers() {
    MultiExpression<String> parsed = Generate.generateFieldMapping(Generate.parseYaml("""
      output:
        - key0: val0
        - __AND__:
            key1: val1
            key2: val2
      """));
    assertEquals(MultiExpression.of(List.of(
      MultiExpression.entry("output", or(
        matchAny("key0", "val0"),
        and(
          matchAny("key1", "val1"),
          matchAny("key2", "val2")
        )
      ))
    )), parsed);
  }

  @Test
  void testParseAndContainingOthers() {
    MultiExpression<String> parsed = Generate.generateFieldMapping(Generate.parseYaml("""
      output:
        __AND__:
          - key1: val1
          - __OR__:
              key2: val2
              key3: val3
      """));
    assertEquals(MultiExpression.of(List.of(
      MultiExpression.entry("output", and(
        matchAny("key1", "val1"),
        or(
          matchAny("key2", "val2"),
          matchAny("key3", "val3")
        )
      ))
    )), parsed);
  }

  @Test
  void testParseContainsKey() {
    MultiExpression<String> parsed = Generate.generateFieldMapping(Generate.parseYaml("""
      output:
        key1: val1
        key2:
      """));
    assertEquals(MultiExpression.of(List.of(
      MultiExpression.entry("output", or(
        matchAny("key1", "val1"),
        matchField("key2")
      ))
    )), parsed);
  }

  @TestFactory
  Stream<DynamicTest> testParseImposm3Mapping() {
    record TestCase(String name, String mapping, String require, String reject, Expression expected) {

      TestCase(String mapping, Expression expected) {
        this(mapping, mapping, null, null, expected);
      }
    }
    return Stream.of(
      new TestCase(
        "key: val", matchAny("key", "val")
      ),
      new TestCase(
        "key: [val1, val2]", matchAny("key", "val1", "val2")
      ),
      new TestCase(
        "key: [\"__any__\"]", matchField("key")
      ),
      new TestCase("reject",
        "key: val",
        "mustkey: mustval",
        null,
        and(
          matchAny("key", "val"),
          matchAny("mustkey", "mustval")
        )
      ),
      new TestCase("require",
        "key: val",
        null,
        "badkey: badval",
        and(
          matchAny("key", "val"),
          not(matchAny("badkey", "badval"))
        )
      ),
      new TestCase("require and reject complex",
        """
          key: val
          key2:
            - val1
            - val2
          """,
        """
          mustkey: mustval
          mustkey2:
            - mustval1
            - mustval2
          """,
        """
          notkey: notval
          notkey2:
            - notval1
            - notval2
          """,
        and(
          or(
            matchAny("key", "val"),
            matchAny("key2", "val1", "val2")
          ),
          matchAny("mustkey", "mustval"),
          matchAny("mustkey2", "mustval1", "mustval2"),
          not(matchAny("notkey", "notval")),
          not(matchAny("notkey2", "notval1", "notval2"))
        )
      )
    ).map(test -> dynamicTest(test.name, () -> {
      Expression parsed = Generate
        .parseImposm3MappingExpression("point", Generate.parseYaml(test.mapping), new Generate.Imposm3Filters(
          Generate.parseYaml(test.reject),
          Generate.parseYaml(test.require)
        ));
      assertEquals(test.expected, parsed.replace(matchType("point"), TRUE).simplify());
    }));
  }

  @Test
  void testTypeMappingTopLevelType() {
    Expression parsed = Generate
      .parseImposm3MappingExpression("point", Generate.parseYaml("""
        key: val
        """), new Generate.Imposm3Filters(null, null));
    assertEquals(and(
      matchAny("key", "val"),
      matchType("point")
    ), parsed);
  }

  @Test
  void testTypeMappings() {
    Map<String, JsonNode> props = new LinkedHashMap<>();
    props.put("points", Generate.parseYaml("""
      key: val
      """));
    props.put("polygons", Generate.parseYaml("""
      key2: val2
      """));
    Expression parsed = Generate
      .parseImposm3MappingExpression(new Generate.Imposm3Table(
        "geometry",
        false,
        List.of(),
        null,
        null,
        props,
        List.of()
      ));
    assertEquals(or(
      and(
        matchAny("key", "val"),
        matchType("point")
      ),
      and(
        matchAny("key2", "val2"),
        matchType("polygon")
      )
    ), parsed);
  }
}
