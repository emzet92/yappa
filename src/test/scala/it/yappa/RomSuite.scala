package it.yappa

import munit.FunSuite

class RomSuite extends FunSuite {
  test("some test") {
    assert(true)
  }

  val cases = List(
    (1, 1, 2),
    (2, 3, 5),
    (10, 5, 15)
  )

  cases.foreach { (a, b, expected) =>
    test(s"$a + $b should equal $expected") {
      assertEquals(a + b, expected)
    }
  }
}
