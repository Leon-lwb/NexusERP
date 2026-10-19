package com.nexus.common.trace;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class Gs1IdentityTest {
 @Test void parenthesizedCompoundPreservesSerialAndBatch() {
  var identity=Gs1Identity.parse("(01)09506000134352(10)LOT1(21)EXT01").orElseThrow();
  assertEquals("EXT01",identity.serial());assertEquals("LOT1",identity.batch());
 }
 @Test void fnc1VariableFieldsAreSeparatedWithoutGuessing() {
  var identity=Gs1Identity.parse("]C1010950600013435210LOT1"+(char)29+"21EXT01").orElseThrow();
  assertEquals("EXT01",identity.serial());
  assertNull(Gs1Identity.parse("010950600013435210LOT121EXT01").orElseThrow().serial());
 }
 @Test void unknownDuplicateOrMalformedAiIsNotReinterpreted() {
  assertTrue(Gs1Identity.parse("(21)A(21)B").isEmpty());assertTrue(Gs1Identity.parse("SN202610180001").isEmpty());
 }
}
