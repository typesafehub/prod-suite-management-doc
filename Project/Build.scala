/*
 * Copyright © 2016 Lightbend, Inc. All rights reserved.
 * No information contained herein may be reproduced or transmitted in any form
 * or by any means without the express written permission of Typesafe, Inc.
 */

import com.typesafe.sbt.SbtScalariform._
import de.heikoseeberger.sbtheader.{HeaderPattern, HeaderPlugin}
import com.lightbend.paradox.sbt.ParadoxPlugin
import sbt._
import sbt.Keys._

import scalariform.formatter.preferences._

object Build extends AutoPlugin {

  import ParadoxPlugin.autoImport._
  
  override def requires =
    plugins.JvmPlugin && HeaderPlugin

  override def trigger =
    allRequirements

  override def projectSettings =
    scalariformSettings ++
    List(
      // Core settings
      organization := "com.lightbend",
      version := sys.env.getOrElse("PACKAGE_VERSION", "0.1.0"),
      licenses := Seq("Apache 2" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
      scalaVersion := "2.11.8",
      scalacOptions ++= List(
        "-unchecked",
        "-deprecation",
        "-feature",
        "-language:_",
        "-target:jvm-1.8",
        "-encoding", "UTF-8",
        "-Xexperimental"
      ),
      // Scalariform settings
      ScalariformKeys.preferences := ScalariformKeys.preferences.value
        .setPreference(AlignSingleLineCaseStatements, true)
        .setPreference(AlignSingleLineCaseStatements.MaxArrowIndent, 100)
        .setPreference(DoubleIndentClassDeclaration, true)
        .setPreference(PreserveDanglingCloseParenthesis, true),
      // Header settings
      HeaderPlugin.autoImport.headers := Map(
        "scala" -> (
          HeaderPattern.cStyleBlockComment,
          """|/*
             | * Copyright © 2016 Lightbend, Inc. All rights reserved.
             | * No information contained herein may be reproduced or transmitted in any form
             | * or by any means without the express written permission of Typesafe, Inc.
             | */
             |
             |""".stripMargin
          ),
        "proto" -> (
          HeaderPattern.cppStyleLineComment,
          """// Copyright © 2016 Lightbend, Inc. All rights reserved.
            |// No information contained herein may be reproduced or transmitted in any form
            |// or by any means without the express written permission of Typesafe, Inc.
            |
            |""".stripMargin
          ),
        "py" -> (
          HeaderPattern.hashLineComment,
          """|# Copyright © 2016 Lightbend, Inc. All rights reserved.
             |# No information contained herein may be reproduced or transmitted in any form
             |# or by any means without the express written permission of Typesafe, Inc.
             |
             |""".stripMargin
          ),
        "conf" -> (
          HeaderPattern.hashLineComment,
          """|# Copyright © 2016 Lightbend, Inc. All rights reserved.
             |# No information contained herein may be reproduced or transmitted in any form
             |# or by any means without the express written permission of Typesafe, Inc.
             |
             |""".stripMargin
          )
      )
    )
}
