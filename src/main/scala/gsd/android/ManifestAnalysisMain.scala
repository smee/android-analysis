/*
 * Copyright (c) 2010 Thorsten Berger <berger@informatik.uni-leipzig.de>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package gsd.android

import java.io.{File, FileFilter}
import kiama.rewriting.Rewriter
import xml.{Node, XML}

/**
 * Created by IntelliJ IDEA.
 * User: berger
 * Date: 05.10.2010
 * Time: 17:11:38
 * To change this template use File | Settings | File Templates.
 */

object ManifestAnalysisMain extends Rewriter{

  val MANIFESTS_FOLDER = new File( "input/manifests" )
  val NS_android = "http://schemas.android.com/apk/res/android"


  case class App( name: String,
                  intentFilter: List[IntentFilter] )
  case class IntentFilter( action: List[Action],
                           category: List[Category],
                           data: List[Data] )
  case class Action( name: String )
  case class Category( name: String)
  case class Data( mimeType: Option[String],
                   scheme: Option[String],
                   host: Option[String],
                   port: Option[String],
                   path: Option[String] )


  def main( args: Array[String] ){

    val manifests = MANIFESTS_FOLDER listFiles manifestsFilter

    val manifestXMLs = Map( manifests.
                            map( XML loadFile ).
                            map( x => ( (x\"@package") text, x ) ): _* )

    println( "Duplicates: " + ( manifests.size - manifestXMLs.size ) )

    // scala XML can get quite ugly, especially getting attributes...

    val apps = manifestXMLs.toList.map{ m =>
      App( m._1, (m._2\\"intent-filter").toList.map{ intf =>
        IntentFilter(
          (intf\"action").toList.map( a => Action( getAttr( a, "name" ) get ) ),
          (intf\"category").toList.map( c => Category( getAttr( c, "name" ) get ) ),
          (intf\"data").toList.map( d => Data( getAttr( d, "mimeType" ),
                                               getAttr( d, "scheme" ),
                                               getAttr( d, "host" ),
                                               getAttr( d, "port" ),
                                               getAttr( d, "path" ) ) )
        )
      })
    }

//    apps foreach println

    val actions = collects{
      case Action( a ) => a
    }(apps)

    val categories = collects{
      case Category( c ) => c
    }(apps)

    val mimeTypes = collects{
      case Data( Some(m), _, _, _, _ ) => m
    }(apps)

    println( "============================\nActions:")
    actions foreach println
    println( "============================\nCategories:")
    categories foreach println
    println( "============================\nMIME Types:")
    mimeTypes foreach println

    println
    println( actions.size + " unique actions" )
    println( categories.size + " unique categories" )
    println( mimeTypes.size + " unique MIME types" )
    println

    val androidPrefix:(String=>Boolean) =
      x => !x.startsWith("com.android.") && !x.startsWith("android.")

    println( "non-android actions:" + actions.filter( androidPrefix ).size )
    println( "non-android categories:" + categories.filter( androidPrefix ).size )
    
  }

  val manifestsFilter = new FileFilter(){
		def accept( p: File ) = p.isFile //&& ( p.getName endsWith ".xml" )
	}

  def getAttr( n: Node, attr: String ): Option[String] =
    n.attribute( NS_android, attr ) match{
      case Some( a ) => Some( a text )
      case None => None
    }

}