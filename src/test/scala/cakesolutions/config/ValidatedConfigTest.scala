// Copyright 2016 Carl Pulley

package cakesolutions.config

import com.typesafe.config.{Config, ConfigException, ConfigFactory}
import org.scalatest.FreeSpec

import scala.concurrent.duration._
import scala.util.{Failure, Success}

object ValidatedConfigTest {
  case object GenericTestFailure extends Exception
  case object NameShouldBeNonEmptyAndLowerCase extends Exception
  case object ShouldBePositive extends Exception
  case object ShouldNotBeNegative extends Exception

  // Permissive case class construction - instances may be altered after creation
  final case class HttpConfig(host: String, port: Int)
  final case class Settings(name: String, timeout: FiniteDuration, http: HttpConfig)
}

class ValidatedConfigTest extends FreeSpec {
  import ValidatedConfigTest._

  private def matchOrFail[Value](value: => Value)(matcher: PartialFunction[Value, Unit]): Unit = {
    matcher.orElse[Value, Unit] { case result => assert(false, result) }(value)
  }

  "parameter checking" - {
    val fakeException = new RuntimeException("fake exception")

    implicit val config = ConfigFactory.parseString(
      """
        |top-level-name = "test"
        |top-level-required = "NOT_SET"
        |top-level-null = null
        |test {
        |  nestedVal = 50.68
        |  nestedDuration = 4 h
        |  nestedList = []
        |  nestedRequired = "UNDEFINED"
        |  context {
        |    valueInt = 30
        |    valueStr = "test string"
        |    valueDuration = 12 ms
        |    valueStrList = [ "addr1:10", "addr2:20", "addr3:30" ]
        |    valueDoubleList = [ 10.2, 20, 0.123 ]
        |  }
        |}
      """.stripMargin)

    "ConfigError toString" in {
      assert(ConfigError().toString == "ConfigError()")
      assert(ConfigError(ValueFailure("some.path", ShouldBePositive)).toString == s"ConfigError(ValueFailure(some.path,$ShouldBePositive))")
    }

    "FileNotFound toString" in {
      assert(FileNotFound("/some/path", GenericTestFailure).toString == s"FileNotFound(/some/path,$GenericTestFailure)")
    }

    "validate method" in {
      assert(validate[String]("top-level-name", GenericTestFailure)("test" == _) == Right("test"))
      assert(validate[String](required("top-level-name"), GenericTestFailure)("test" == _) == Right("test"))
      assert(validate[String](required("top-level-name", "NOT_SET"), GenericTestFailure)("test" == _) == Right("test"))
      assert(validate[String](required("top-level-required", "NOT_SET"), GenericTestFailure)("test" == _) == Left(ValueFailure("top-level-required", RequiredValueNotSet)))
      assert(validate[String](required("top-level-null"), GenericTestFailure)("test" == _) == Left(ValueFailure("top-level-null", RequiredValueNotSet)))
      assert(validate[Double]("test.nestedVal", GenericTestFailure)(50.68 == _) == Right(50.68))
      assert(validate[Double](required("test.nestedVal", "NOT_SET"), GenericTestFailure)(50.68 == _) == Right(50.68))
      assert(validate[FiniteDuration]("test.nestedDuration", GenericTestFailure)(4.hours == _) == Right(4.hours))
      assert(validate[FiniteDuration](required("test.nestedDuration", "NOT_SET"), GenericTestFailure)(4.hours == _) == Right(4.hours))
      assert(validate[List[Double]]("test.nestedList", GenericTestFailure)(_.isEmpty) == Right(List.empty[Double]))
      assert(validate[List[Double]](required("test.nestedList", "NOT_SET"), GenericTestFailure)(_.isEmpty) == Right(List.empty[Double]))
      assert(validate[String](required("test.nestedRequired", "NOT_SET"), GenericTestFailure)("UNDEFINED" == _) == Right("UNDEFINED"))
      assert(validate[String](required("test.nestedRequired", "UNDEFINED"), GenericTestFailure)("UNDEFINED" == _) == Left(ValueFailure("test.nestedRequired", RequiredValueNotSet)))
      assert(validate[Int]("test.context.valueInt", GenericTestFailure)(30 == _) == Right(30))
      assert(validate[Int](required("test.context.valueInt", "NOT_SET"), GenericTestFailure)(30 == _) == Right(30))
      assert(validate[String]("test.context.valueStr", GenericTestFailure)("test string" == _) == Right("test string"))
      assert(validate[String](required("test.context.valueStr", "NOT_SET"), GenericTestFailure)("test string" == _) == Right("test string"))
      assert(validate[FiniteDuration]("test.context.valueDuration", GenericTestFailure)(12.milliseconds == _) == Right(12.milliseconds))
      assert(validate[FiniteDuration](required("test.context.valueDuration", "NOT_SET"), GenericTestFailure)(12.milliseconds == _) == Right(12.milliseconds))
      assert(validate[List[String]]("test.context.valueStrList", GenericTestFailure)(List("addr1:10", "addr2:20", "addr3:30") == _) == Right(List("addr1:10", "addr2:20", "addr3:30")))
      assert(validate[List[String]](required("test.context.valueStrList", "NOT_SET"), GenericTestFailure)(List("addr1:10", "addr2:20", "addr3:30") == _) == Right(List("addr1:10", "addr2:20", "addr3:30")))
      assert(validate[List[Double]]("test.context.valueDoubleList", GenericTestFailure)(List(10.2, 20, 0.123) == _) == Right(List(10.2, 20, 0.123)))
      assert(validate[List[Double]](required("test.context.valueDoubleList", "NOT_SET"), GenericTestFailure)(List(10.2, 20, 0.123) == _) == Right(List(10.2, 20, 0.123)))
      assert(validate[List[Int]]("test.context.valueDoubleList", GenericTestFailure)(_ => true) == Right(List(10, 20, 0)))
      assert(validate[List[Int]](required("test.context.valueDoubleList", "NOT_SET"), GenericTestFailure)(_ => true) == Right(List(10, 20, 0)))
      assert(validate[List[String]]("test.context.valueDoubleList", GenericTestFailure)(_ => true) == Right(List("10.2", "20", "0.123")))
      assert(validate[List[String]](required("test.context.valueDoubleList", "NOT_SET"), GenericTestFailure)(_ => true) == Right(List("10.2", "20", "0.123")))

      assert(validate[String]("top-level-name", GenericTestFailure)(_ => false) == Left(ValueFailure("top-level-name", GenericTestFailure)))
      matchOrFail(validate[String]("invalid-path", GenericTestFailure)(_ => true)) {
        case Left(ValueFailure("invalid-path", _: ConfigException.Missing)) =>
          assert(true)
      }
      matchOrFail(validate[String](required("invalid-path"), GenericTestFailure)(_ => true)) {
        case Left(ValueFailure("invalid-path", RequiredValueNotSet)) =>
          assert(true)
      }
      matchOrFail(validate[String](required("invalid-path", "NOT_SET"), GenericTestFailure)(_ => true)) {
        case Left(ValueFailure("invalid-path", _: ConfigException.Missing)) =>
          assert(true)
      }
      matchOrFail(validate[String]("test.invalid-path", GenericTestFailure)(_ => true)) {
        case Left(ValueFailure("test.invalid-path", _: ConfigException.Missing)) =>
          assert(true)
      }
      matchOrFail(validate[String](required("test.invalid-path", "NOT_SET"), GenericTestFailure)(_ => true)) {
        case Left(ValueFailure("test.invalid-path", _: ConfigException.Missing)) =>
          assert(true)
      }
      matchOrFail(validate[Int]("top-level-name", GenericTestFailure)(_ => true)) {
        case Left(ValueFailure("top-level-name", _: ConfigException.WrongType)) =>
          assert(true)
      }
      matchOrFail(validate[Int](required("top-level-name", "NOT_SET"), GenericTestFailure)(_ => true)) {
        case Left(ValueFailure("top-level-name", _: ConfigException.WrongType)) =>
          assert(true)
      }
      matchOrFail(validate[String]("top-level-name", GenericTestFailure)(_ => throw fakeException)) {
        case Left(ValueFailure("top-level-name", `fakeException`)) =>
          assert(true)
      }
      matchOrFail(validate[String](required("top-level-name", "NOT_SET"), GenericTestFailure)(_ => throw fakeException)) {
        case Left(ValueFailure("top-level-name", `fakeException`)) =>
          assert(true)
      }
    }

    "unchecked method" in {
      assert(unchecked[String]("top-level-name") == Right("test"))
      assert(unchecked[String](required("top-level-name")) == Right("test"))
      assert(unchecked[String](required("top-level-name", "NOT_SET")) == Right("test"))
      assert(unchecked[String](required("top-level-required", "NOT_SET")) == Left(ValueFailure("top-level-required", RequiredValueNotSet)))
      assert(unchecked[Double]("test.nestedVal") == Right(50.68))
      assert(unchecked[Double](required("test.nestedVal", "NOT_SET")) == Right(50.68))
      assert(unchecked[FiniteDuration]("test.nestedDuration") == Right(4.hours))
      assert(unchecked[FiniteDuration](required("test.nestedDuration", "NOT_SET")) == Right(4.hours))
      assert(unchecked[List[Double]]("test.nestedList") == Right(List.empty[Double]))
      assert(unchecked[List[Double]](required("test.nestedList", "NOT_SET")) == Right(List.empty[Double]))
      assert(unchecked[String](required("test.nestedRequired", "NOT_SET")) == Right("UNDEFINED"))
      assert(unchecked[String](required("test.nestedRequired", "UNDEFINED")) == Left(ValueFailure("test.nestedRequired", RequiredValueNotSet)))
      assert(unchecked[Int]("test.context.valueInt") == Right(30))
      assert(unchecked[Int](required("test.context.valueInt", "NOT_SET")) == Right(30))
      assert(unchecked[String]("test.context.valueStr") == Right("test string"))
      assert(unchecked[String](required("test.context.valueStr", "NOT_SET")) == Right("test string"))
      assert(unchecked[FiniteDuration]("test.context.valueDuration") == Right(12.milliseconds))
      assert(unchecked[FiniteDuration](required("test.context.valueDuration", "NOT_SET")) == Right(12.milliseconds))
      assert(unchecked[List[String]]("test.context.valueStrList") == Right(List("addr1:10", "addr2:20", "addr3:30")))
      assert(unchecked[List[String]](required("test.context.valueStrList", "NOT_SET")) == Right(List("addr1:10", "addr2:20", "addr3:30")))
      assert(unchecked[List[Double]]("test.context.valueDoubleList") == Right(List(10.2, 20, 0.123)))
      assert(unchecked[List[Double]](required("test.context.valueDoubleList", "NOT_SET")) == Right(List(10.2, 20, 0.123)))
      assert(unchecked[List[Int]]("test.context.valueDoubleList") == Right(List(10, 20, 0)))
      assert(unchecked[List[Int]](required("test.context.valueDoubleList", "NOT_SET")) == Right(List(10, 20, 0)))
      assert(unchecked[List[String]]("test.context.valueDoubleList") == Right(List("10.2", "20", "0.123")))
      assert(unchecked[List[String]](required("test.context.valueDoubleList", "NOT_SET")) == Right(List("10.2", "20", "0.123")))

      matchOrFail(unchecked[String]("invalid-path")) {
        case Left(ValueFailure("invalid-path", NullValue)) =>
          assert(true)
      }
      matchOrFail(unchecked[String](required("invalid-path"))) {
        case Left(ValueFailure("invalid-path", NullValue)) =>
          assert(true)
      }
      matchOrFail(unchecked[String](required("invalid-path", "NOT_SET"))) {
        case Left(ValueFailure("invalid-path", NullValue)) =>
          assert(true)
      }
      matchOrFail(unchecked[String]("test.invalid-path")) {
        case Left(ValueFailure("test.invalid-path", NullValue)) =>
          assert(true)
      }
      matchOrFail(unchecked[String](required("test.invalid-path", "NOT_SET"))) {
        case Left(ValueFailure("test.invalid-path", NullValue)) =>
          assert(true)
      }
      matchOrFail(unchecked[Int]("top-level-name")) {
        case Left(ValueFailure("top-level-name", _)) =>
          assert(true)
      }
      matchOrFail(unchecked[Int](required("top-level-name", "NOT_SET"))) {
        case Left(ValueFailure("top-level-name", _)) =>
          assert(true)
      }
    }

    "case class building methods" - {
      case class TestSettings(str: String, int: Int, double: Double, duration: FiniteDuration, strList: List[String], doubleList: List[Double])

      "validated building" in {
        val testConfig1 = via("test") { implicit config =>
          build[TestSettings](
            validate[String]("context.valueStr", GenericTestFailure)(_ => true),
            validate[Int]("context.valueInt", GenericTestFailure)(_ => true),
            validate[Double]("nestedVal", GenericTestFailure)(_ => true),
            validate[FiniteDuration]("nestedDuration", GenericTestFailure)(_ => true),
            validate[List[String]]("context.valueStrList", GenericTestFailure)(_ => true),
            validate[List[Double]]("context.valueDoubleList", GenericTestFailure)(_ => true)
          )
        }
        matchOrFail(testConfig1) {
          case Right(TestSettings("test string", 30, 50.68, duration, List("addr1:10", "addr2:20", "addr3:30"), List(10.2, 20, 0.123))) =>
            assert(duration == 4.hours)
        }
        val testConfig2 = via("test") { implicit config =>
          build[TestSettings](
            validate[Int]("context.valueStr", GenericTestFailure)(_ => true),
            validate[Int]("context.valueInt", GenericTestFailure)(_ => true),
            validate[Double](required("nestedVal", "NOT_SET"), GenericTestFailure)(_ => true),
            validate[FiniteDuration]("nestedDuration", GenericTestFailure)(_ => true),
            validate[List[String]]("context.valueStrList", GenericTestFailure)(_ => true),
            validate[List[Double]]("context.valueDoubleList", GenericTestFailure)(_ => true)
          )
        }
        matchOrFail(testConfig2) {
          case Left(NestedConfigError(ConfigError(ValueFailure("test.context.valueStr", _: ConfigException.WrongType)))) =>
            assert(true)
        }
        val testConfig3 = via("test") { implicit config =>
          build[TestSettings](
            validate[Int]("context.valueStr", GenericTestFailure)(_ => true),
            validate[Int](required("context.valueInt", "NOT_SET"), GenericTestFailure)(_ => true),
            validate[Double]("bad-path.nestedVal", GenericTestFailure)(_ => true),
            validate[FiniteDuration]("nestedDuration", GenericTestFailure)(_ => true),
            validate[List[String]]("context.valueStrList", GenericTestFailure)(_ => false),
            validate[List[Double]]("context.valueDoubleList", GenericTestFailure)(_ => true)
          )
        }
        matchOrFail(testConfig3) {
          case Left(NestedConfigError(ConfigError(ValueFailure("test.context.valueStr", _: ConfigException.WrongType), ValueFailure("test.bad-path.nestedVal", _: ConfigException.Missing), ValueFailure("test.context.valueStrList", GenericTestFailure)))) =>
            assert(true)
        }
        val testConfig4 = via("test") { implicit config =>
          build[TestSettings](
            validate[Int]("context.valueStr", GenericTestFailure)(_ => true),
            validate[Int]("context.valueInt", GenericTestFailure)(_ => true),
            validate[Double]("nestedVal", GenericTestFailure)(_ => true),
            validate[FiniteDuration](required("nestedRequired", "NOT_SET"), GenericTestFailure)(_ => true),
            validate[List[String]]("context.valueStrList", GenericTestFailure)(_ => false),
            validate[List[Double]]("context.valueDoubleList", GenericTestFailure)(_ => true)
          )
        }
        matchOrFail(testConfig4) {
          case Left(NestedConfigError(err @ ConfigError(ValueFailure("test.context.valueStr", _: ConfigException.WrongType), ValueFailure("test.nestedRequired", _: ConfigException.BadValue), ValueFailure("test.context.valueStrList", GenericTestFailure)))) =>
            assert(true)
        }
        val testConfig5 = via("test") { implicit config =>
          build[TestSettings](
            validate[Int]("context.valueStr", GenericTestFailure)(_ => true),
            validate[Int]("context.valueInt", GenericTestFailure)(_ => true),
            validate[Double]("nestedVal", GenericTestFailure)(_ => true),
            validate[FiniteDuration](required("nestedRequired", "UNDEFINED"), GenericTestFailure)(_ => true),
            validate[List[String]]("context.valueStrList", GenericTestFailure)(_ => false),
            validate[List[Double]]("context.valueDoubleList", GenericTestFailure)(_ => true)
          )
        }
        matchOrFail(testConfig5) {
          case Left(NestedConfigError(err @ ConfigError(ValueFailure("test.context.valueStr", _: ConfigException.WrongType), ValueFailure("test.nestedRequired", RequiredValueNotSet), ValueFailure("test.context.valueStrList", GenericTestFailure)))) =>
            assert(true)
        }
      }

      "unchecked building" in {
        val testConfig1 = via("test") { implicit config =>
          build[TestSettings](
            unchecked[String]("context.valueStr"),
            unchecked[Int]("context.valueInt"),
            unchecked[Double]("nestedVal"),
            unchecked[FiniteDuration]("nestedDuration"),
            unchecked[List[String]]("context.valueStrList"),
            unchecked[List[Double]]("context.valueDoubleList")
          )
        }
        matchOrFail(testConfig1) {
          case Right(TestSettings("test string", 30, 50.68, duration, List("addr1:10", "addr2:20", "addr3:30"), List(10.2, 20, 0.123))) =>
            assert(duration == 4.hours)
        }
        val testConfig2 = via("test") { implicit config =>
          build[TestSettings](
            unchecked[Int]("context.valueStr"),
            unchecked[Int]("context.valueInt"),
            unchecked[Double]("nestedVal"),
            unchecked[FiniteDuration]("nestedDuration"),
            unchecked[List[String]]("context.valueStrList"),
            unchecked[List[Double]]("context.valueDoubleList")
          )
        }
        matchOrFail(testConfig2) {
          case Left(NestedConfigError(ConfigError(ValueFailure("test.context.valueStr", _: ConfigException.WrongType)))) =>
            assert(true)
        }
        val testConfig3 = via("test") { implicit config =>
          build[TestSettings](
            unchecked[Int]("context.valueStr"),
            unchecked[Int]("context.valueInt"),
            unchecked[Double]("bad-path.nestedVal"),
            unchecked[FiniteDuration]("nestedDuration"),
            unchecked[List[String]]("context.valueStrList"),
            unchecked[List[Double]]("context.valueDoubleList")
          )
        }
        matchOrFail(testConfig3) {
          case Left(NestedConfigError(ConfigError(ValueFailure("test.context.valueStr", _: ConfigException.WrongType), ValueFailure("test.bad-path.nestedVal", NullValue)))) =>
            assert(true)
        }
        val testConfig4 = via("test") { implicit config =>
          build[TestSettings](
            unchecked[Int]("context.valueStr"),
            unchecked[Int]("context.valueInt"),
            unchecked[Double]("nestedVal"),
            unchecked[FiniteDuration](required("nestedRequired", "NOT_SET")),
            unchecked[List[String]]("context.valueStrList"),
            unchecked[List[Double]]("context.valueDoubleList")
          )
        }
        matchOrFail(testConfig4) {
          case Left(NestedConfigError(ConfigError(ValueFailure("test.context.valueStr", _: ConfigException.WrongType), ValueFailure("test.nestedRequired", _: ConfigException.BadValue)))) =>
            assert(true)
        }
        val testConfig5 = via("test") { implicit config =>
          build[TestSettings](
            unchecked[Int]("context.valueStr"),
            unchecked[Int]("context.valueInt"),
            unchecked[Double]("nestedVal"),
            unchecked[FiniteDuration](required("nestedRequired", "UNDEFINED")),
            unchecked[List[String]]("context.valueStrList"),
            unchecked[List[Double]]("context.valueDoubleList")
          )
        }
        matchOrFail(testConfig5) {
          case Left(NestedConfigError(ConfigError(ValueFailure("test.context.valueStr", _: ConfigException.WrongType), ValueFailure("test.nestedRequired", RequiredValueNotSet)))) =>
            assert(true)
        }
      }
    }
  }

  "Ensure config files may be correctly parsed and validated" - {
    "invalid files fail to load" in {
      val validatedConfig =
        validateConfig("non-existent.conf") { implicit config =>
          build[Settings](
            validate[String]("name", NameShouldBeNonEmptyAndLowerCase)(_.matches("[a-z0-9_-]+")),
            validate[FiniteDuration]("http.timeout", ShouldNotBeNegative)(_ >= 0.seconds),
            via("http") { implicit config =>
              build[HttpConfig](
                unchecked[String]("host"),
                validate[Int]("port", ShouldBePositive)(_ > 0)
              )
            }
          )
        }

      matchOrFail(validatedConfig) {
        case Failure(FileNotFound("non-existent.conf", _)) =>
          assert(true)
      }
    }

    "files referencing non-existent (required) includes fail to load" in {
      val validatedConfig =
        validateConfig("invalid.conf") { implicit config =>
          build[Settings](
            validate[String]("name", NameShouldBeNonEmptyAndLowerCase)(_.matches("[a-z0-9_-]+")),
            validate[FiniteDuration]("http.timeout", ShouldNotBeNegative)(_ >= 0.seconds),
            via("http") { implicit config =>
              build[HttpConfig](
                unchecked[String]("host"),
                validate[Int]("port", ShouldBePositive)(_ > 0)
              )
            }
          )
        }

      matchOrFail(validatedConfig) {
        case Failure(FileNotFound(_, _: ConfigException)) =>
          assert(true)
      }
    }

    "valid file but with validation failure" in {
      case object NotAHttpPort extends Exception

      val validatedConfig =
        validateConfig("http.conf") { implicit config =>
          build[HttpConfig](
            unchecked[String]("http.host"),
            validate[Int]("http.port", NotAHttpPort)(_ != 80)
          )
        }

      assert(validatedConfig == Failure(ConfigError(ValueFailure("http.port", NotAHttpPort))))
    }

    "valid file but required values not set" in {
      val validatedConfig =
        validateConfig("application.conf") { implicit config =>
          build[Settings](
            validate[String]("name", NameShouldBeNonEmptyAndLowerCase)(_.matches("[a-z0-9_-]+")),
            validate[FiniteDuration](required("http.heartbeat", "NOT_SET"), ShouldNotBeNegative)(_ >= 0.seconds),
            via("http") { implicit config =>
              build[HttpConfig](
                unchecked[String]("host"),
                validate[Int]("port", ShouldBePositive)(_ > 0)
              )
            }
          )
        }

      matchOrFail(validatedConfig) {
        case Failure(ConfigError(ValueFailure("http.heartbeat", RequiredValueNotSet))) =>
          assert(true)
      }
    }

    "with environment variable overrides" in {
      val envMapping: Config = ConfigFactory.parseString(
        """
          |env {
          |  AKKA_HOST = docker-local
          |  AKKA_PORT = 2552
          |  AKKA_BIND_HOST = google.co.uk
          |  AKKA_BIND_PORT = 123
          |
          |  HTTP_ADDR = 192.168.99.100
          |  HTTP_PORT = 5678
          |
          |  required.HEARTBEAT = 20s
          |}
        """.
          stripMargin
      )
      implicit val config = envMapping.withFallback(ConfigFactory.parseResourcesAnySyntax("application.conf")).resolve()

      val validatedConfig =
        build[Settings](
          validate[String]("name", NameShouldBeNonEmptyAndLowerCase)(_.matches("[a-z0-9_-]+")),
          validate[FiniteDuration](required("http.heartbeat", "NOT_SET"), ShouldNotBeNegative)(_ >= 0.seconds),
          via("http") { implicit config =>
            build[HttpConfig](
              unchecked[String]("host"),
              validate[Int]("port", ShouldBePositive)(_ > 0)
            )
          }
        )

      assert(validatedConfig.isRight)
      matchOrFail(validatedConfig) {
        case Right(Settings("test-data", timeout, HttpConfig("192.168.99.100", 5678))) =>
          assert(timeout == 20.seconds)
      }
    }

    "using system environment variable overrides" in {
      val validatedConfig =
        validateConfig("application.conf") { implicit config =>
          build[Settings](
            validate[String]("name", NameShouldBeNonEmptyAndLowerCase)(_.matches("[a-z0-9_-]+")),
            validate[FiniteDuration]("http.timeout", ShouldNotBeNegative)(_ >= 0.seconds),
            via("http") { implicit config =>
              build[HttpConfig](
                unchecked[String]("host"),
                validate[Int]("port", ShouldBePositive)(_ > 0)
              )
            }
          )
        }

      assert(validatedConfig.isSuccess)
      matchOrFail(validatedConfig) {
        case Success(Settings("test-data", timeout, HttpConfig("localhost", 80))) =>
          assert(timeout == 30.seconds)
      }
    }
  }
}