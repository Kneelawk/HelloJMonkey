package org.kneelawk.hellojmonkey

import com.jme3.app.SimpleApplication
import com.jme3.asset.AssetLocator
import com.jme3.asset.AssetManager
import com.jme3.asset.AssetKey

object HelloJMonkey extends SimpleApplication {
  def main(args: Array[String]) {
    println("Starting up...")
    setShowSettings(false)
    start
  }
  
  def simpleInitApp() {
    println("Init simple app")
  }
}