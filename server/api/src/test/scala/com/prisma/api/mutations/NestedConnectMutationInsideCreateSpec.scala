package com.prisma.api.mutations

import com.prisma.api.ApiBaseSpec
import com.prisma.api.database.DatabaseQueryBuilder
import com.prisma.shared.project_dsl.SchemaDsl
import org.scalatest.{FlatSpec, Matchers}

class NestedConnectMutationInsideCreateSpec extends FlatSpec with Matchers with ApiBaseSpec {

  "a P1! to C1! relation with the child already in a relation" should "error when connecting by id since old required parent relation would be broken" in {
    val project = SchemaDsl() { schema =>
      val parent = schema.model("Parent").field_!("p", _.String, isUnique = true)
      val child  = schema.model("Child").field_!("c", _.String, isUnique = true).oneToOneRelation_!("parentReq", "childReq", parent)
    }
    database.setup(project)

    val child1Id = server
      .executeQuerySimple(
        """mutation {
                            |  createParent(data: {
                            |    p: "p1"
                            |    childReq: {
                            |      create: {c: "c1"}
                            |    }
                            |  }){
                            |    childReq{
                            |       id
                            |    }
                            |  }
                            |}""".stripMargin,
        project
      )
      .pathAsString("data.createParent.childReq.id")

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ChildToParent").as[Int]) should be(Vector(1))

    server.executeQuerySimpleThatMustFail(
      s"""
         |mutation {
         |  createParent(data:{
         |    p: "p2"
         |    childReq: {connect: {id: "$child1Id"}}
         |  }){
         |    childReq {
         |      c
         |    }
         |  }
         |}
      """.stripMargin,
      project,
      errorCode = 3042,
      errorContains = "The change you are trying to make would violate the required relation '_ChildToParent' between Child and Parent"
    )

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ChildToParent").as[Int]) should be(Vector(1))
  }

  "a P1! to C1 relation with the child already in a relation" should "should fail on existing old parent" in {
    val project = SchemaDsl() { schema =>
      val child = schema.model("Child").field_!("c", _.String, isUnique = true)
      schema.model("Parent").field_!("p", _.String, isUnique = true).oneToOneRelation_!("childReq", "parentOpt", child, isRequiredOnOtherField = false)
    }
    database.setup(project)

    val child1Id = server
      .executeQuerySimple(
        """mutation {
          |  createParent(data: {
          |    p: "p1"
          |    childReq: {
          |      create: {c: "c1"}
          |    }
          |  }){
          |    childReq{
          |       id
          |    }
          |  }
          |}""".stripMargin,
        project
      )
      .pathAsString("data.createParent.childReq.id")

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ParentToChild").as[Int]) should be(Vector(1))

    server.executeQuerySimpleThatMustFail(
      s"""
         |mutation {
         |  createParent(data:{
         |    p: "p2"
         |    childReq: {connect: {id: "$child1Id"}}
         |  }){
         |    childReq {
         |      c
         |    }
         |  }
         |}
      """.stripMargin,
      project,
      errorCode = 3042,
      errorContains = "The change you are trying to make would violate the required relation '_ParentToChild' between Parent and Child"
    )

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ParentToChild").as[Int]) should be(Vector(1))
  }

  "a P1! to C1  relation with the child not in a relation" should "be connectable through a nested mutation by id" in {
    val project = SchemaDsl() { schema =>
      val child = schema.model("Child").field_!("c", _.String, isUnique = true)
      schema.model("Parent").field_!("p", _.String, isUnique = true).oneToOneRelation_!("childReq", "parentOpt", child, isRequiredOnOtherField = false)
    }
    database.setup(project)

    val child1Id = server
      .executeQuerySimple(
        """mutation {
          |  createChild(data: {c: "c1"})
          |  {
          |    id
          |  }
          |}""".stripMargin,
        project
      )
      .pathAsString("data.createChild.id")

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ParentToChild").as[Int]) should be(Vector(0))

    val res = server.executeQuerySimple(
      s"""
         |mutation {
         |  createParent(data:{
         |    p: "p2"
         |    childReq: {connect: {id: "$child1Id"}}
         |  }){
         |    childReq {
         |      c
         |    }
         |  }
         |}
      """.stripMargin,
      project
    )

    res.toString should be("""{"data":{"createParent":{"childReq":{"c":"c1"}}}}""")
    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ParentToChild").as[Int]) should be(Vector(1))
  }

  "a P1 to C1  relation with the child already in a relation" should "be connectable through a nested mutation by id if the child is already in a relation" in {
    val project = SchemaDsl() { schema =>
      val child = schema.model("Child").field_!("c", _.String, isUnique = true)
      schema.model("Parent").field_!("p", _.String, isUnique = true).oneToOneRelation("childOpt", "parentOpt", child)
    }
    database.setup(project)

    val child1Id = server
      .executeQuerySimple(
        """mutation {
          |  createParent(data: {
          |    p: "p1"
          |    childOpt: {
          |      create: {c: "c1"}
          |    }
          |  }){
          |    childOpt{
          |       id
          |    }
          |  }
          |}""".stripMargin,
        project
      )
      .pathAsString("data.createParent.childOpt.id")

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ParentToChild").as[Int]) should be(Vector(1))

    val res = server.executeQuerySimple(
      s"""
         |mutation {
         |  createParent(data:{
         |    p: "p2"
         |    childOpt: {connect: {id: "$child1Id"}}
         |  }){
         |    childOpt {
         |      c
         |    }
         |  }
         |}
      """.stripMargin,
      project
    )

    res.toString should be("""{"data":{"createParent":{"childOpt":{"c":"c1"}}}}""")

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ParentToChild").as[Int]) should be(Vector(1))
  }

  "a P1 to C1  relation with the child without a relation" should "be connectable through a nested mutation by id" in {
    val project = SchemaDsl() { schema =>
      val child = schema.model("Child").field_!("c", _.String, isUnique = true)
      schema.model("Parent").field_!("p", _.String, isUnique = true).oneToOneRelation("childOpt", "parentOpt", child)
    }
    database.setup(project)

    val child1Id = server
      .executeQuerySimple(
        """mutation {
          |  createChild(data: {c: "c1"})
          |  {
          |    id
          |  }
          |}""".stripMargin,
        project
      )
      .pathAsString("data.createChild.id")

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ParentToChild").as[Int]) should be(Vector(0))

    val res = server.executeQuerySimple(
      s"""
         |mutation {
         |  createParent(data:{
         |    p: "p2"
         |    childOpt: {connect: {id: "$child1Id"}}
         |  }){
         |    childOpt {
         |      c
         |    }
         |  }
         |}
      """.stripMargin,
      project
    )

    res.toString should be("""{"data":{"createParent":{"childOpt":{"c":"c1"}}}}""")

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ParentToChild").as[Int]) should be(Vector(1))
  }

  "a PM to C1!  relation with the child already in a relation" should "be connectable through a nested mutation by unique" in {
    val project = SchemaDsl() { schema =>
      val child = schema.model("Child").field_!("c", _.String, isUnique = true)
      schema.model("Parent").field_!("p", _.String, isUnique = true).oneToManyRelation_!("childrenOpt", "parentReq", child)
    }
    database.setup(project)

    server.executeQuerySimple(
      """mutation {
          |  createParent(data: {
          |    p: "p1"
          |    childrenOpt: {
          |      create: {c: "c1"}
          |    }
          |  }){
          |    childrenOpt{
          |       c
          |    }
          |  }
          |}""".stripMargin,
      project
    )

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ParentToChild").as[Int]) should be(Vector(1))

    val res = server.executeQuerySimple(
      s"""
         |mutation {
         |  createParent(data:{
         |    p: "p2"
         |    childrenOpt: {connect: {c: "c1"}}
         |  }){
         |    childrenOpt {
         |      c
         |    }
         |  }
         |}
      """.stripMargin,
      project
    )

    res.toString should be("""{"data":{"createParent":{"childrenOpt":[{"c":"c1"}]}}}""")

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ParentToChild").as[Int]) should be(Vector(1))
  }

  "a P1 to C1!  relation with the child already in a relation" should "be connectable through a nested mutation by unique" in {
    val project = SchemaDsl() { schema =>
      val parent = schema.model("Parent").field_!("p", _.String, isUnique = true)
      val child =
        schema.model("Child").field_!("c", _.String, isUnique = true).oneToOneRelation_!("parentReq", "childOpt", parent, isRequiredOnOtherField = false)
    }
    database.setup(project)

    server.executeQuerySimple(
      """mutation {
          |  createParent(data: {
          |    p: "p1"
          |    childOpt: {
          |      create: {c: "c1"}
          |    }
          |  }){
          |    childOpt{
          |       c
          |    }
          |  }
          |}""".stripMargin,
      project
    )
    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ChildToParent").as[Int]) should be(Vector(1))

    val res = server.executeQuerySimple(
      s"""
         |mutation {
         |  createParent(data:{
         |    p: "p2"
         |    childOpt: {connect: {c: "c1"}}
         |  }){
         |    childOpt {
         |      c
         |    }
         |  }
         |}
      """.stripMargin,
      project
    )

    res.toString should be("""{"data":{"createParent":{"childOpt":{"c":"c1"}}}}""")

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ChildToParent").as[Int]) should be(Vector(1))
  }

  "a PM to C1  relation with the child already in a relation" should "be connectable through a nested mutation by unique" in {
    val project = SchemaDsl() { schema =>
      val child = schema.model("Child").field_!("c", _.String, isUnique = true)
      schema.model("Parent").field_!("p", _.String, isUnique = true).oneToManyRelation("childrenOpt", "parentOpt", child)
    }
    database.setup(project)

    server
      .executeQuerySimple(
        """mutation {
          |  createParent(data: {
          |    p: "p1"
          |    childrenOpt: {
          |      create: [{c: "c1"}, {c: "c2"}]
          |    }
          |  }){
          |    childrenOpt{
          |       c
          |    }
          |  }
          |}""".stripMargin,
        project
      )

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ParentToChild").as[Int]) should be(Vector(2))

    // we are even resilient against multiple identical connects here -> twice connecting to c2

    val res = server.executeQuerySimple(
      s"""
         |mutation {
         |  createParent(data:{
         |    p: "p2"
         |    childrenOpt: {connect: [{c: "c1"},{c: "c2"},{c: "c2"}]}
         |  }){
         |    childrenOpt {
         |      c
         |    }
         |  }
         |}
      """.stripMargin,
      project
    )

    res.toString should be("""{"data":{"createParent":{"childrenOpt":[{"c":"c1"},{"c":"c2"}]}}}""")

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ParentToChild").as[Int]) should be(Vector(2))
  }

  "a PM to C1  relation with the child without a relation" should "be connectable through a nested mutation by unique" in {
    val project = SchemaDsl() { schema =>
      val child = schema.model("Child").field_!("c", _.String, isUnique = true)
      schema.model("Parent").field_!("p", _.String, isUnique = true).oneToManyRelation("childrenOpt", "parentOpt", child)
    }
    database.setup(project)

    val child1Id = server
      .executeQuerySimple(
        """mutation {
          |  createChild(data: {c: "c1"})
          |  {
          |    id
          |  }
          |}""".stripMargin,
        project
      )
      .pathAsString("data.createChild.id")

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ParentToChild").as[Int]) should be(Vector(0))

    val res = server.executeQuerySimple(
      s"""
         |mutation {
         |  createParent(data:{
         |    p: "p2"
         |    childrenOpt: {connect: {c: "c1"}}
         |  }){
         |    childrenOpt {
         |      c
         |    }
         |  }
         |}
      """.stripMargin,
      project
    )

    res.toString should be("""{"data":{"createParent":{"childrenOpt":[{"c":"c1"}]}}}""")

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ParentToChild").as[Int]) should be(Vector(1))
  }

  "a PM to C1  relation with a child without a relation" should "error if also trying to connect to a non-existing node" in {
    val project = SchemaDsl() { schema =>
      val child = schema.model("Child").field_!("c", _.String, isUnique = true)
      schema.model("Parent").field_!("p", _.String, isUnique = true).oneToManyRelation("childrenOpt", "parentOpt", child)
    }
    database.setup(project)

    val child1Id = server
      .executeQuerySimple(
        """mutation {
          |  createChild(data: {c: "c1"})
          |  {
          |    id
          |  }
          |}""".stripMargin,
        project
      )
      .pathAsString("data.createChild.id")

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ParentToChild").as[Int]) should be(Vector(0))

    server.executeQuerySimpleThatMustFail(
      s"""
         |mutation {
         |  createParent(data:{
         |    p: "p2"
         |    childrenOpt: {connect: [{c: "c1"}, {c: "DOES NOT EXIST"}]}
         |  }){
         |    childrenOpt {
         |      c
         |    }
         |  }
         |}
      """.stripMargin,
      project,
      errorCode = 3039,
      errorContains = "No Node for the model Child with value DOES NOT EXIST for c found."
    )

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ParentToChild").as[Int]) should be(Vector(0))
  }

  "a P1! to CM  relation with the child already in a relation" should "be connectable through a nested mutation by unique" in {
    val project = SchemaDsl() { schema =>
      val parent = schema.model("Parent").field_!("p", _.String, isUnique = true)
      val child  = schema.model("Child").field_!("c", _.String, isUnique = true).oneToManyRelation_!("parentsOpt", "childReq", parent)
    }
    database.setup(project)

    server.executeQuerySimple(
      """mutation {
        |  createParent(data: {
        |    p: "p1"
        |    childReq: {
        |      create: {c: "c1"}
        |    }
        |  }){
        |    childReq{
        |       c
        |    }
        |  }
        |}""".stripMargin,
      project
    )

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ChildToParent").as[Int]) should be(Vector(1))

    val res = server.executeQuerySimple(
      s"""
         |mutation {
         |  createParent(data:{
         |    p: "p2"
         |    childReq: {connect: {c: "c1"}}
         |  }){
         |    childReq {
         |      c
         |    }
         |  }
         |}
      """.stripMargin,
      project
    )

    res.toString should be("""{"data":{"createParent":{"childReq":{"c":"c1"}}}}""")

    server.executeQuerySimple(s"""query{children{parentsOpt{p}}}""", project).toString should be(
      """{"data":{"children":[{"parentsOpt":[{"p":"p1"},{"p":"p2"}]}]}}""")

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ChildToParent").as[Int]) should be(Vector(2))
  }

  "a P1! to CM  relation with the child not already in a relation" should "be connectable through a nested mutation by unique" in {
    val project = SchemaDsl() { schema =>
      val parent = schema.model("Parent").field_!("p", _.String, isUnique = true)
      val child  = schema.model("Child").field_!("c", _.String, isUnique = true).oneToManyRelation_!("parentsOpt", "childReq", parent)
    }
    database.setup(project)

    server.executeQuerySimple(
      """mutation {
        |  createChild(data: {c: "c1"}){
        |       c
        |  }
        |}""".stripMargin,
      project
    )

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ChildToParent").as[Int]) should be(Vector(0))

    val res = server.executeQuerySimple(
      s"""
         |mutation {
         |  createParent(data:{
         |    p: "p2"
         |    childReq: {connect: {c: "c1"}}
         |  }){
         |    childReq {
         |      c
         |    }
         |  }
         |}
      """.stripMargin,
      project
    )

    res.toString should be("""{"data":{"createParent":{"childReq":{"c":"c1"}}}}""")

    server.executeQuerySimple(s"""query{children{parentsOpt{p}}}""", project).toString should be("""{"data":{"children":[{"parentsOpt":[{"p":"p2"}]}]}}""")

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ChildToParent").as[Int]) should be(Vector(1))
  }

  "a P1 to CM  relation with the child already in a relation" should "be connectable through a nested mutation by unique" in {
    val project = SchemaDsl() { schema =>
      val parent = schema.model("Parent").field_!("p", _.String, isUnique = true)
      val child  = schema.model("Child").field_!("c", _.String, isUnique = true).oneToManyRelation("parentsOpt", "childOpt", parent)
    }
    database.setup(project)

    server.executeQuerySimple(
      """mutation {
        |  createParent(data: {
        |    p: "p1"
        |    childOpt: {
        |      create: {c: "c1"}
        |    }
        |  }){
        |    childOpt{
        |       c
        |    }
        |  }
        |}""".stripMargin,
      project
    )

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ChildToParent").as[Int]) should be(Vector(1))

    val res = server.executeQuerySimple(
      s"""
         |mutation {
         |  createParent(data:{
         |    p: "p2"
         |    childOpt: {connect: {c: "c1"}}
         |  }){
         |    childOpt{
         |      c
         |    }
         |  }
         |}
      """.stripMargin,
      project
    )

    res.toString should be("""{"data":{"createParent":{"childOpt":{"c":"c1"}}}}""")

    server.executeQuerySimple(s"""query{children{parentsOpt{p}}}""", project).toString should be(
      """{"data":{"children":[{"parentsOpt":[{"p":"p1"},{"p":"p2"}]}]}}""")

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ChildToParent").as[Int]) should be(Vector(2))
  }

  "a P1 to CM  relation with the child not already in a relation" should "be connectable through a nested mutation by unique" in {
    val project = SchemaDsl() { schema =>
      val parent = schema.model("Parent").field_!("p", _.String, isUnique = true)
      val child  = schema.model("Child").field_!("c", _.String, isUnique = true).oneToManyRelation("parentsOpt", "childOpt", parent)
    }
    database.setup(project)

    server.executeQuerySimple(
      """mutation {
        |  createChild(data: {c: "c1"}){
        |       c
        |  }
        |}""".stripMargin,
      project
    )

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ChildToParent").as[Int]) should be(Vector(0))

    val res = server.executeQuerySimple(
      s"""
         |mutation {
         |  createParent(data:{
         |    p: "p2"
         |    childOpt: {connect: {c: "c1"}}
         |  }){
         |    childOpt {
         |      c
         |    }
         |  }
         |}
      """.stripMargin,
      project
    )

    res.toString should be("""{"data":{"createParent":{"childOpt":{"c":"c1"}}}}""")

    server.executeQuerySimple(s"""query{children{parentsOpt{p}}}""", project).toString should be("""{"data":{"children":[{"parentsOpt":[{"p":"p2"}]}]}}""")

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ChildToParent").as[Int]) should be(Vector(1))
  }

  "a PM to CM  relation with the children already in a relation" should "be connectable through a nested mutation by unique" in {
    val project = SchemaDsl() { schema =>
      val parent = schema.model("Parent").field_!("p", _.String, isUnique = true)
      val child  = schema.model("Child").field_!("c", _.String, isUnique = true).manyToManyRelation("parentsOpt", "childrenOpt", parent)
    }
    database.setup(project)

    server.executeQuerySimple(
      """mutation {
        |  createParent(data: {
        |    p: "p1"
        |    childrenOpt: {
        |      create: [{c: "c1"},{c: "c2"}]
        |    }
        |  }){
        |    childrenOpt{
        |       c
        |    }
        |  }
        |}""".stripMargin,
      project
    )

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ChildToParent").as[Int]) should be(Vector(2))

    val res = server.executeQuerySimple(
      s"""
         |mutation {
         |  createParent(data:{
         |    p: "p2"
         |    childrenOpt: {connect: [{c: "c1"}, {c: "c2"}]}
         |  }){
         |    childrenOpt{
         |      c
         |    }
         |  }
         |}
      """.stripMargin,
      project
    )

    res.toString should be("""{"data":{"createParent":{"childrenOpt":[{"c":"c1"},{"c":"c2"}]}}}""")

    server.executeQuerySimple(s"""query{children{parentsOpt{p}}}""", project).toString should be(
      """{"data":{"children":[{"parentsOpt":[{"p":"p1"},{"p":"p2"}]},{"parentsOpt":[{"p":"p1"},{"p":"p2"}]}]}}""")

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ChildToParent").as[Int]) should be(Vector(4))
  }

  "a PM to CM  relation with the child not already in a relation" should "be connectable through a nested mutation by unique" in {
    val project = SchemaDsl() { schema =>
      val parent = schema.model("Parent").field_!("p", _.String, isUnique = true)
      val child  = schema.model("Child").field_!("c", _.String, isUnique = true).manyToManyRelation("parentsOpt", "childrenOpt", parent)
    }
    database.setup(project)

    server.executeQuerySimple(
      """mutation {
        |  createChild(data: {c: "c1"}){
        |       c
        |  }
        |}""".stripMargin,
      project
    )

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ChildToParent").as[Int]) should be(Vector(0))

    val res = server.executeQuerySimple(
      s"""
         |mutation {
         |  createParent(data:{
         |    p: "p2"
         |    childrenOpt: {connect: {c: "c1"}}
         |  }){
         |    childrenOpt {
         |      c
         |    }
         |  }
         |}
      """.stripMargin,
      project
    )

    res.toString should be("""{"data":{"createParent":{"childrenOpt":[{"c":"c1"}]}}}""")

    server.executeQuerySimple(s"""query{children{parentsOpt{p}}}""", project).toString should be("""{"data":{"children":[{"parentsOpt":[{"p":"p2"}]}]}}""")

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ChildToParent").as[Int]) should be(Vector(1))
  }

  "a PM to CM  relation without a backrelation" should "be connectable through a nested mutation by unique" in {
    val project = SchemaDsl() { schema =>
      val role = schema.model("Role").field_!("r", _.String, isUnique = true)
      val user = schema.model("User").field_!("u", _.String, isUnique = true).manyToManyRelation("roles", "notexposed", role, includeOtherField = false)
    }
    database.setup(project)

    server.executeQuerySimple(
      """mutation {
        |  createRole(data: {r: "r1"}){
        |       r
        |  }
        |}""".stripMargin,
      project
    )

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_UserToRole").as[Int]) should be(Vector(0))

    val res = server.executeQuerySimple(
      s"""
         |mutation {
         |  createUser(data:{
         |    u: "u2"
         |    roles: {connect: {r: "r1"}}
         |  }){
         |    roles {
         |      r
         |    }
         |  }
         |}
      """.stripMargin,
      project
    )

    res.toString should be("""{"data":{"createUser":{"roles":[{"r":"r1"}]}}}""")

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_UserToRole").as[Int]) should be(Vector(1))
  }

  "a many relation" should "be connectable through a nested mutation by id" in {
    val project = SchemaDsl() { schema =>
      val comment = schema.model("Comment").field_!("text", _.String)
      schema.model("Todo").oneToManyRelation("comments", "todo", comment)
    }
    database.setup(project)

    val comment1Id = server.executeQuerySimple("""mutation { createComment(data: {text: "comment1"}){ id } }""", project).pathAsString("data.createComment.id")
    val comment2Id = server.executeQuerySimple("""mutation { createComment(data: {text: "comment2"}){ id } }""", project).pathAsString("data.createComment.id")

    val result = server.executeQuerySimple(
      s"""
        |mutation {
        |  createTodo(data:{
        |    comments: {
        |      connect: [{id: "$comment1Id"}, {id: "$comment2Id"}]
        |    }
        |  }){
        |    id
        |    comments {
        |      id
        |      text
        |    }
        |  }
        |}
      """.stripMargin,
      project
    )
    mustBeEqual(
      actual = result.pathAsJsValue("data.createTodo.comments").toString,
      expected = s"""[{"id":"$comment1Id","text":"comment1"},{"id":"$comment2Id","text":"comment2"}]"""
    )
  }

  "a many relation" should "throw a proper error if connected by wrong id" in {
    val project = SchemaDsl() { schema =>
      val comment = schema.model("Comment").field_!("text", _.String)
      schema.model("Todo").oneToManyRelation("comments", "todo", comment)
    }
    database.setup(project)

    server.executeQuerySimpleThatMustFail(
      s"""
         |mutation {
         |  createTodo(data:{
         |    comments: {
         |      connect: [{id: "DoesNotExist"}]
         |    }
         |  }){
         |    id
         |    comments {
         |      id
         |      text
         |    }
         |  }
         |}
      """.stripMargin,
      project,
      errorCode = 3039,
      errorContains = "No Node for the model Comment with value DoesNotExist for id found."
    )
  }

  "a many relation" should "throw a proper error if connected by wrong id the other way around" in {
    val project = SchemaDsl() { schema =>
      val comment = schema.model("Comment").field_!("text", _.String)
      schema.model("Todo").oneToManyRelation("comments", "todo", comment)
    }
    database.setup(project)

    server.executeQuerySimpleThatMustFail(
      s"""
         |mutation {
         |  createComment(data:{
         |    text: "bla"
         |    todo: {
         |      connect: {id: "DoesNotExist"}
         |    }
         |  }){
         |    id
         |  }
         |}
      """.stripMargin,
      project,
      errorCode = 3039,
      errorContains = "No Node for the model Todo with value DoesNotExist for id found."
    )
  }

  "a many relation" should "throw a proper error if the id of a wrong model is provided" in {
    val project = SchemaDsl() { schema =>
      val comment = schema.model("Comment").field_!("text", _.String)
      schema.model("Todo").oneToManyRelation("comments", "todo", comment)
    }
    database.setup(project)

    val comment1Id = server.executeQuerySimple("""mutation { createComment(data: {text: "comment1"}){ id } }""", project).pathAsString("data.createComment.id")
    val comment2Id = server.executeQuerySimple("""mutation { createComment(data: {text: "comment2"}){ id } }""", project).pathAsString("data.createComment.id")

    val todoId = server
      .executeQuerySimple(
        s"""
         |mutation {
         |  createTodo(data:{
         |    comments: {
         |      connect: [{id: "$comment1Id"}, {id: "$comment2Id"}]
         |    }
         |  }){
         |    id
         |  }
         |}
      """.stripMargin,
        project
      )
      .pathAsString("data.createTodo.id")

    server.executeQuerySimpleThatMustFail(
      s"""
         |mutation {
         |  createTodo(data:{
         |    comments: {
         |      connect: [{id: "$todoId"}]
         |    }
         |  }){
         |    id
         |  }
         |}
      """.stripMargin,
      project,
      errorCode = 3039,
      errorContains = s"No Node for the model Comment with value $todoId for id found."
    )

  }

  "a many relation" should "be connectable through a nested mutation by any unique argument" in {
    val project = SchemaDsl() { schema =>
      val comment = schema.model("Comment").field_!("text", _.String).field_!("alias", _.String, isUnique = true)
      schema.model("Todo").oneToManyRelation("comments", "todo", comment)
    }
    database.setup(project)

    val comment1Alias = server
      .executeQuerySimple("""mutation { createComment(data: {text: "text comment1", alias: "comment1"}){ alias } }""", project)
      .pathAsString("data.createComment.alias")
    val comment2Alias = server
      .executeQuerySimple("""mutation { createComment(data: {text: "text comment2", alias: "comment2"}){ alias } }""", project)
      .pathAsString("data.createComment.alias")

    val result = server.executeQuerySimple(
      s"""
         |mutation {
         |  createTodo(data:{
         |    comments: {
         |      connect: [{alias: "$comment1Alias"}, {alias: "$comment2Alias"}]
         |    }
         |  }){
         |    id
         |    comments {
         |      alias
         |      text
         |    }
         |  }
         |}
      """.stripMargin,
      project
    )
    mustBeEqual(
      actual = result.pathAsJsValue("data.createTodo.comments").toString,
      expected = s"""[{"alias":"$comment1Alias","text":"text comment1"},{"alias":"$comment2Alias","text":"text comment2"}]"""
    )
  }

  "a many relation" should "be connectable through a nested mutation by any unique argument in the opposite direction" in {
    val project = SchemaDsl() { schema =>
      val comment = schema.model("Comment")
      schema.model("Todo").field_!("title", _.String).oneToManyRelation("comments", "todo", comment).field_!("alias", _.String, isUnique = true)
    }
    database.setup(project)

    val todoAlias = server
      .executeQuerySimple("""mutation { createTodo(data: {title: "the title", alias: "todo1"}){ alias } }""", project)
      .pathAsString("data.createTodo.alias")

    val result = server.executeQuerySimple(
      s"""
         |mutation {
         |  createComment(
         |    data: {
         |      todo: {
         |        connect: { alias: "$todoAlias"}
         |      }
         |    }
         |  )
         |  {
         |    todo {
         |      alias
         |      title
         |    }
         |  }
         |}
      """.stripMargin,
      project
    )
    mustBeEqual(
      actual = result.pathAsJsValue("data.createComment.todo").toString,
      expected = s"""{"alias":"$todoAlias","title":"the title"}"""
    )
  }
}
