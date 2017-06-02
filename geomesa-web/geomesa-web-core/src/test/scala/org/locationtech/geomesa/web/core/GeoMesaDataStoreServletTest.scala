/***********************************************************************
 * Copyright (c) 2013-2017 Commonwealth Computer Research, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License, Version 2.0
 * which accompanies this distribution and is available at
 * http://www.opensource.org/licenses/apache2.0.php.
 ***********************************************************************/

package org.locationtech.geomesa.web.core

import java.net.URLEncoder
import java.nio.file.Files

import org.json4s.{DefaultFormats, Formats}
import org.junit.runner.RunWith
import org.locationtech.geomesa.utils.cache.FilePersistence
import org.locationtech.geomesa.utils.classpath.PathUtils
import org.scalatra.Ok
import org.scalatra.json.NativeJsonSupport
import org.scalatra.test.specs2.MutableScalatraSpec
import org.specs2.runner.JUnitRunner
import org.specs2.specification.{Fragments, Step}

@RunWith(classOf[JUnitRunner])
class GeoMesaDataStoreServletTest extends MutableScalatraSpec {

  sequential

  val tmpDir = Files.createTempDirectory("gmServletTest")

  def urlEncode(s: String): String = URLEncoder.encode(s, "UTF-8")

  // cleanup tmp dir after tests run
  override def map(fragments: => Fragments) = super.map(fragments) ^ Step {
    PathUtils.deleteRecursively(tmpDir)
  }

  var calledTest = false

  addServlet(new GeoMesaDataStoreServlet with NativeJsonSupport {
    override def root: String = ""
    override val persistence: FilePersistence = new FilePersistence(tmpDir.toFile, "servlet")

    override protected implicit val jsonFormats: Formats = DefaultFormats

    before() {
      contentType = formats("json")
    }

    get("/test") {
      try {
        withDataStore((ds) => { calledTest = true; Ok() })
      } catch {
        case e: Exception => handleError(s"Error creating index:", e)
      }
    }
  }, "/*")

  implicit val formats: Formats = DefaultFormats

  val dsParams = Map("instanceId" -> "GeoMesaDataStoreServletTest", "zookeepers" -> "zoo", "user" -> "root",
    "password" -> "", "tableName" -> "GeoMesaDataStoreServlet", "useMock" -> "true")

  val jsonParams =
    dsParams.filterKeys(_ != "password").map { case (k, v) => s""""$k":"$v"""" } ++ Seq(""""password":"***"""")

  "GeoMesaDataStoreServlet" should {
    "register a datastore" in {
      post("/ds/servlettest", dsParams) {
        status mustEqual 200
      }
    }
    "allow datastore to be used by alias" in {
      calledTest = false
      get("test?alias=servlettest") {
        status mustEqual 200
        calledTest must beTrue
      }
    }
    "list registered datastore without password" in {
      get("/ds/servlettest") {
        status mustEqual 200
        body must startWith("{")
        body must endWith("}")
        forall(jsonParams)(param => body must contain(param))
      }
    }
    "list all registered datastores without passwords" in {
      get("/ds") {
        status mustEqual 200
        body must startWith("{")
        body must endWith("}")
        body must contain(""""servlettest":{""")
        forall(jsonParams)(param => body must contain(param))
      }
    }
    "delete a registered datastore" in {
      delete("/ds/servlettest") {
        status mustEqual 200
      }
      get("/ds/servlettest") {
        status mustEqual 404
      }
      get("/ds") {
        status mustEqual 200
        body mustEqual "{}"
      }
    }
  }
}
