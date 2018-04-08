package org.kneelawk.hellojmonkey

import com.jme3.app.SimpleApplication
import com.jme3.asset.AssetLocator
import com.jme3.asset.AssetManager
import com.jme3.asset.AssetKey
import com.jme3.scene.shape.Box
import com.jme3.scene.Geometry
import com.jme3.material.Material
import com.jme3.math.ColorRGBA
import org.log4s._

object HelloJMonkey extends SimpleApplication {
  val log = getLogger

  def main(args: Array[String]) {
    log.info("Starting up...")
    setShowSettings(false)
    start
  }

  def simpleInitApp() {
    log.info("Init simple app")
    val box = new Box(1, 1, 1)
    val geom = new Geometry("Box", box)
    val mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md")
    mat.setColor("Color", ColorRGBA.Blue)
    geom.setMaterial(mat)
    rootNode.attachChild(geom)
  }
}