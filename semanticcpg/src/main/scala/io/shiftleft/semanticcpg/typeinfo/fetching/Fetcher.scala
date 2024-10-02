package io.shiftleft.semanticcpg.typeinfo.fetching

import io.shiftleft.semanticcpg.typeinfo.{PackageIdentifier, PackageMetadata, Version}
import io.shiftleft.semanticcpg.typeinfo.dependencies.DirectDependency

import java.nio.file.{Path, Paths}
import scala.util.Try

/** ...
 * 
 * All public methods return an array of bytes or some type containing arrays of bytes. These byte arrays represent
 * the data that was downloaded by deriving fetchers. */
abstract class Fetcher extends AutoCloseable {
    protected final case class FetcherResult(path: ServerPath, data: Array[Byte])

    /** Much of fetching is building paths to resources and converting between different types of paths: paths that
     * generic fetching understands (e.g., path to version info or dependency files), paths that a specific fetcher
     * understands (e.g., git repo paths), and filesystem paths. To avoid mixing incompatible Path instances, instances
     * of ServerPath are given to abstract methods that deriving types should implement; these deriving types can use
     * ServerPaths as they are or convert between other fetcher-specific Path types. */
    protected final case class ServerPath(path: Path) {
        def getMetaDataPath: ServerPath = {
            ServerPath(path.resolve("metadata").resolve("metadata.ion"))
        }
        
        def getDirectDepsPath(version: Version): ServerPath = {
            ServerPath(path.resolve("dependencies").resolve(version.toFetcherStr).resolve("direct.ion"))
        }
        
        def getTransitiveDepsPath(version: Version): ServerPath = {
            ServerPath(path.resolve("dependencies").resolve(version.toFetcherStr).resolve("transitive.ion"))
        }
        
        def getVersionPath(version: Version): ServerPath = {
            ServerPath(path.resolve(version.toFetcherStr))
        }
        
        def getTypeFilePath(typeName: String): ServerPath = {
            ServerPath(path.resolve(typeName + ".ion"))
        }
        
        def resolve(other: String): ServerPath = {
            ServerPath(path.resolve(other))
        }
        
        override def toString: String = path.toString
    }
    protected object ServerPath {
        def build(pid: PackageIdentifier): ServerPath = {
            val packageDirPath = Paths.get(pid.platform.toString).resolve(pid.name)
            ServerPath(packageDirPath)
        }
    }
    
    /** The metadata file contains a list of all versions stored stored for this package identifier and
     * a list of all types stored for this versioned package identifier. */
    def fetchMetaData(pid: PackageIdentifier): Array[Byte] = {
        val infoFilePath = ServerPath.build(pid).getMetaDataPath
        val downloadResults = downloadFiles(List(infoFilePath))
        downloadResults.head.data
    }
    
    /** Returns a map from type name -> an input stream to read data about this type. The type name keys are type
     * short names, the NAME property of a TYPE_DECL in a CPG. */
    def fetchTypeData(pid: PackageIdentifier, version: Version, typeNames: List[String]): Map[String, Array[Byte]] = {
        val versionedPackageDir = ServerPath.build(pid).getVersionPath(version)
        val typePaths = typeNames.map(versionedPackageDir.getTypeFilePath)
        val downloadResults = downloadFiles(typePaths).map(_.data)
        Map.from(typeNames.zip(downloadResults))
    }
    
    def fetchDirectDependencies(pid: PackageIdentifier, version: Version): Array[Byte] = {
        val directDepPath = ServerPath.build(pid).getDirectDepsPath(version)
        val downloadResults = downloadFiles(List(directDepPath))
        downloadResults.head.data
    }

    def fetchDirectDependencies(versionedPids: List[(PackageIdentifier, Version)]): Map[(PackageIdentifier, Version), Array[Byte]] = {
        val directDepPaths = versionedPids.map((pid, version) => ServerPath.build(pid).getDirectDepsPath(version))
        val downloadResults = downloadFiles(directDepPaths).map(_.data)
        Map.from(versionedPids.zip(downloadResults))
    }
    
    def fetchTransitiveDependencies(pid: PackageIdentifier, version: Version): Array[Byte] = {
        val transitiveDepPath = ServerPath.build(pid).getTransitiveDepsPath(version)
        val downloadResults = downloadFiles(List(transitiveDepPath))
        downloadResults.head.data
    }
    
    /** This method should be overridden by deriving fetchers. This method should guarantee that 
     * return(i) is the download result for paths(i) */
    protected def downloadFiles(paths: List[ServerPath]): List[FetcherResult]
}
