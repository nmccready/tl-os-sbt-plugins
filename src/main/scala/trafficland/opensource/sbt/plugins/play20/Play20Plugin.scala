package trafficland.opensource.sbt.plugins.play20

import sbt._
import sbt.Keys._

object Play20Plugin extends Plugin {

  lazy val plug = Seq(
    productionConfigFile <<= (baseDirectory) { (baseDir) => Some(baseDir / "conf" / "prod.conf") },
    stageConfigFile <<= (baseDirectory) { (baseDir) => Some(baseDir / "conf" / "stage.conf") },
    startScriptName := "start",
    startScriptJavaCommand := "java",
    startScriptJavaOptions := None,
    appInfoPackage := "controllers",
    dist <<= distTask,
    distStage <<= distStageTask,
    distCustom <<= distCustomTask,
    sourceGenerators in Compile <+= (sourceManaged in Compile, name, version, organizationName, appInfoPackage) map { (outDir, appName, appVersion, orgName, appInfoPkg) =>
      writeVersion(outDir, appName, appVersion, orgName, appInfoPkg)
    }
  )

  val playPackageEverything = TaskKey[Seq[File]]("play-package-everything")
  val distDirectory = SettingKey[File]("play-dist")
  val startScriptName = SettingKey[String]("start-script")
  val startScriptJavaCommand = SettingKey[String]("start-script-java-command")
  val startScriptJavaOptions = SettingKey[Option[String]]("start-script-java-options")
  val productionConfigFile = SettingKey[Option[File]]("production-config")
  val stageConfigFile = SettingKey[Option[File]]("stage-config")
  val appInfoPackage = SettingKey[String]("app-info-package")
  val dist = TaskKey[File]("dist", "Build the standalone production application package")
  val distStage = TaskKey[File]("dist-stage", "Build the standalone staging application package")
  val distCustom = InputKey[File]("dist-custom", "Build the standalone application package with custom configuration file")
  val distTask = (distDirectory, baseDirectory, playPackageEverything, dependencyClasspath in Runtime, target, productionConfigFile, startScriptName, startScriptJavaCommand, startScriptJavaOptions, normalizedName, version, name, state).map {
    (distDir, root, packaged, dependencies, target, productionConfig, startScript, javaCommand, javaOpts, id, version, name, stream) =>
      distribution(distDir, root, packaged, dependencies, target, productionConfig, startScript, javaCommand, javaOpts, id, version, name, stream)
  }

  val distStageTask = (distDirectory, baseDirectory, playPackageEverything, dependencyClasspath in Runtime, target, stageConfigFile, startScriptName, startScriptJavaCommand, startScriptJavaOptions, normalizedName, version, name, state).map {
    (distDir, root, packaged, dependencies, target, stageConfig, startScript, javaCommand, javaOpts, id, version, name, stream) =>
      distribution(distDir, root, packaged, dependencies, target, stageConfig, startScript, javaCommand, javaOpts, id, version, name, stream)
  }

  val distCustomTask = inputTask { argsTask =>
    (argsTask, distDirectory, baseDirectory, playPackageEverything, dependencyClasspath in Runtime, target, startScriptName, startScriptJavaCommand, startScriptJavaOptions, normalizedName, version, name, state).map {
      (args, distDir, root, packaged, dependencies, target, startScript, javaCommand, javaOpts, id, version, name, stream) =>
        val customConfig = args.headOption.map(fn => file(fn))
        customConfig match {
          case Some(_) => distribution(distDir, root, packaged, dependencies, target, customConfig, startScript, javaCommand, javaOpts, id, version, name, stream)
          case None => sys.error("No configuration file specified")
        }
    }
  }

  def distribution(distDir: File, root: File,
                   packaged: Seq[File], dependencies: Id[Keys.Classpath],
                   target: File, customConfig: Option[File], start: String,
                   jCommand: String, jOpts: Option[String], id: String, version: String,
                   name: String, stream: State) = {

    val packageName = name + "-" + version
    stream.log.info("Creating package named %s.".format(packageName))
    val packageDirectory = name
    stream.log.info("Writing package to %s.".format(packageDirectory))
    val zip = distDir / (packageName + ".zip")
    stream.log.info("Creating zip file %s.".format(zip.toString))

    IO.delete(distDir)
    IO.createDirectory(distDir)

    val dependenciesListToZipLocationMappings = getDependencyToZipLocationMappings(dependencies) ++ packaged.map(jar => jar -> ("/lib/" + jar.getName))
    val startScriptLocation = target / start
    val customConfigFilename = customConfig.map(f => Some(f.getName)).getOrElse(None)

    writeStartScript(startScriptLocation, dependenciesListToZipLocationMappings, customConfigFilename, jCommand, jOpts)

    val scripts = Seq(startScriptLocation -> (packageDirectory + "/" + start))
    val other = Seq((root / "README") -> (packageDirectory + "/README"))
    val productionConfigFile = customConfigFilename.map(fn => target / fn).getOrElse(target / "application.conf")
    val prodApplicationConf = getConfigFileToZipLocationMappings(customConfig, productionConfigFile, packageDirectory)
    val defaultApplicationConf = Seq(root / "conf" / "application.conf" -> (packageDirectory + "/conf/application.conf"))

    IO.zip(dependenciesListToZipLocationMappings.map { case (jar, path) => jar -> (packageDirectory + "/" + path) } ++ scripts ++ other ++ prodApplicationConf ++ defaultApplicationConf, zip)
    IO.delete(startScriptLocation)
    IO.delete(productionConfigFile)

    stream.log.info("")
    stream.log.info(name + " has been packaged.  The package can be found at " + zip.getCanonicalPath + "!")
    stream.log.info("ready to stage")
    stream.log.info("")

    zip
  }

  def getConfigFileToZipLocationMappings(customConfig:Option[File], productionConfigFile:File, packageDirectory:String) : Seq[(File, String)] = {
    customConfig.map { customConfigFile =>
      IO.copyFile(customConfigFile, productionConfigFile)
      Seq(productionConfigFile -> (packageDirectory + "/conf/" + customConfigFile.getName))
    }.getOrElse(Nil)
  }

  def writeStartScript(scriptLocation:File, dependencies:Seq[(File, String)], customConfigFilename:Option[String], javaCommand: String, javaOptions: Option[String]) {
    IO.write(scriptLocation,
      """#!/usr/bin/env sh
scriptdir=`dirname $0`
classpath=""" + dependencies.map { case (jar, path) => "$scriptdir/" + path }.mkString("\"", ":", "\"") + """
exec """ + javaCommand + """ $* -cp $classpath """ + javaOptions.map(opts => opts + " ")
.getOrElse("")
  + customConfigFilename.map(fn => "-Dconfig.file=`dirname $0`/conf/" + fn + " ")
  .getOrElse("-Dconfig.file=`dirname $0`/conf/application.conf ")
  + """play.core.server.NettyServer `dirname $0` $*""" /* */ )
  }

  def getDependencyToZipLocationMappings(dependencies:Id[Keys.Classpath]) : Seq[(File, String)] = dependencies.filter(_.data.ext == "jar").map { dependency =>
    val filename = for {
      module <- dependency.metadata.get(AttributeKey[ModuleID]("module-id"))
      artifact <- dependency.metadata.get(AttributeKey[Artifact]("artifact"))
    } yield {
      module.organization + "." + module.name + "-" + Option(artifact.name.replace(module.name, ""))
      .filterNot(_.isEmpty).map(_ + "-").getOrElse("") + module.revision + ".jar"
    }
    val path = ("lib/" + filename.getOrElse(dependency.data.getName))
    dependency.data -> path
  }

  def writeVersion(outDir: File, appName:String, appVersion:String, organizationName:String, appInfoPackage: String) = {
    val file = outDir / "AppInfo.scala"
    IO.write(file,
      """package %s

object AppInfo {

  protected lazy val info = new AppInfo()
  lazy val version = info.version
  lazy val name = info.name
  lazy val vendor = info.vendor
}

class AppInfo {
  val version = "%s"
  val name = "%s"
  val vendor = "%s"
}""".format(appInfoPackage, appVersion, appName, organizationName))
    Seq(file)
  }
}
