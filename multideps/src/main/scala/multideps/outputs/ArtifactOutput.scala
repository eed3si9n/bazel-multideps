package multideps.outputs

import multideps.configs.DependencyConfig
import multideps.diagnostics.MultidepsEnrichments.XtensionDependency

import coursier.core.Dependency
import coursier.util.Artifact
import org.typelevel.paiges.Doc

final case class ArtifactOutput(
    index: ResolutionIndex,
    outputs: collection.Map[String, ArtifactOutput],
    dependency: Dependency,
    config: DependencyConfig,
    artifact: Artifact,
    artifactSha256: String,
    sourcesArtifact: Option[Artifact] = None,
    sourcesArtifactSha256: Option[String] = None
) {
  // Bazel workspace names may contain only A-Z, a-z, 0-9, '-', '_' and '.'
  val label: String =
    dependency.repr.replaceAll("[^a-zA-Z0-9-\\.]", "_")
  val repr: String =
    s"""|Artifact(
        |  dep = "${label}",
        |  url = "${artifact.url}",
        |  sha = "${artifactSha256}"
        |)""".stripMargin
  val org = dependency.module.organization.value
  val moduleName = dependency.module.name.value
  val version = dependency.version
  // pprint.log(dependency.configRepr)
  val mavenLabel: String =
    s"@maven//:${org}/${moduleName}-${version}${config.classifierRepr}.jar"
  lazy val dependencies: Seq[String] =
    index.dependencies
      .getOrElse(config.toId, Nil)
      .iterator
      .flatMap(d => outputs.get(index.reconciledDependency(d).repr))
      .map(_.label)
      .filterNot(_ == label)
      .toSeq
      .distinct
  def httpFile: TargetOutput =
    TargetOutput(
      kind = "http_file",
      "name" -> Docs.literal(label),
      "urls" -> Docs.array(artifact.url),
      "sha256" -> Docs.literal(artifactSha256)
    )
  def genrule: TargetOutput =
    TargetOutput(
      kind = "genrule",
      "name" -> Docs.literal(s"genrules/$label"),
      "srcs" -> Docs.array(s"@${label}//file"),
      "outs" -> Docs.array(mavenLabel),
      "cmd" -> Docs.literal("cp $< $@")
    )
  def scalaImport: TargetOutput =
    TargetOutput(
      kind = "scala_import",
      "name" -> Docs.literal(label),
      "jars" -> Docs.array(mavenLabel),
      "deps" -> Docs.array(dependencies: _*),
      "exports" -> Docs.array(dependencies: _*),
      "tags" -> Docs.array(
        s"jvm_module=${dependency.module.repr}",
        s"jvm_version=${dependency.version}"
      ),
      "visibility" -> Docs.array("//visibility:public")
    )
  def build: Doc =
    genrule.toDoc /
      scalaImport.toDoc
}
