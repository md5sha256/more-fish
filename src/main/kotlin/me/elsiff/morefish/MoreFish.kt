package me.elsiff.morefish

import co.aikar.commands.PaperCommandManager
import me.elsiff.morefish.command.MainCommand
import me.elsiff.morefish.configuration.Config
import me.elsiff.morefish.dao.DaoFactory
import me.elsiff.morefish.fishing.FishingListener
import me.elsiff.morefish.fishing.MutableFishTypeTable
import me.elsiff.morefish.fishing.catchhandler.CatchBroadcaster
import me.elsiff.morefish.fishing.catchhandler.CatchHandler
import me.elsiff.morefish.fishing.catchhandler.CompetitionRecordAdder
import me.elsiff.morefish.fishing.catchhandler.NewFirstBroadcaster
import me.elsiff.morefish.fishing.competition.FishingCompetition
import me.elsiff.morefish.fishing.competition.FishingCompetitionHost
import me.elsiff.morefish.gui.GuiOpener
import me.elsiff.morefish.gui.GuiRegistry
import me.elsiff.morefish.hooker.ProtocolLibHooker
import me.elsiff.morefish.hooker.VaultHooker
import me.elsiff.morefish.item.FishItemStackConverter
import me.elsiff.morefish.shop.FishShop
import me.elsiff.morefish.update.UpdateChecker
import me.elsiff.morefish.update.UpdateNotifierListener
import me.elsiff.morefish.util.OneTickScheduler
import org.bukkit.plugin.java.JavaPlugin

/**
 * Created by elsiff on 2018-12-20.
 */
class MoreFish : JavaPlugin() {
    val protocolLib = ProtocolLibHooker()
    val vault = VaultHooker()

    val guiRegistry = GuiRegistry(this)
    val guiOpener = GuiOpener(guiRegistry)
    val oneTickScheduler = OneTickScheduler(this)
    val fishTypeTable = MutableFishTypeTable()
    val competition = FishingCompetition()
    val competitionHost = FishingCompetitionHost(this, competition)
    val converter = FishItemStackConverter(this, fishTypeTable)
    val fishShop = FishShop(guiRegistry, guiOpener, oneTickScheduler, converter, vault)
    val globalCatchHandlers: List<CatchHandler> = listOf(
        CatchBroadcaster(),
        NewFirstBroadcaster(competition),
        CompetitionRecordAdder(competition)
    )
    val updateChecker = UpdateChecker(22926, this.description.version)

    override fun onEnable() {
        DaoFactory.init(this)

        protocolLib.hookIfEnabled(server)
        vault.hookIfEnabled(server)

        applyConfig()

        server.pluginManager.run {
            val fishingListener =
                FishingListener(fishTypeTable, converter, competition, globalCatchHandlers)
            registerEvents(fishingListener, this@MoreFish)
        }

        val commands = PaperCommandManager(this)
        val mainCommand = MainCommand(this, competitionHost, fishShop)
        commands.registerCommand(mainCommand)

        if (!isSnapshotVersion()) {
            updateChecker.check()
            if (updateChecker.hasNewVersion()) {
                val notifier = UpdateNotifierListener(updateChecker.newVersion)
                server.pluginManager.registerEvents(notifier, this)
            }
        }

        logger.info("Plugin has been enabled.")

        if (Config.standard.boolean("general.auto-start")) {
            competitionHost.openCompetition()
        }
    }

    override fun onDisable() {
        guiRegistry.clear(true)
        logger.info("Plugin has been disabled.")
    }

    private fun isSnapshotVersion(): Boolean {
        return this.description.version.contains("SNAPSHOT", true)
    }

    fun applyConfig() {
        Config.load(this)
        Config.customItemStackLoader.protocolLib = protocolLib

        fishTypeTable.clear()
        fishTypeTable.putAll(Config.fishTypeMapLoader.loadFrom(Config.fish))
        logger.info("Loaded ${fishTypeTable.rarities.size} rarities and ${fishTypeTable.types.size} fish types")
    }
}