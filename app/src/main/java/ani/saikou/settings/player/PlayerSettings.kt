package ani.saikou.settings.player

import java.io.Serializable

data class PlayerSettings(
    //Video can be nuked
    var videoInfo: Boolean = true,
    var defaultSpeed: Int = 5,
    var cursedSpeeds: Boolean = false,
    var resize: Int = 0,

    //Subtitles  hmmm
    var subtitles: Boolean = true,
    var primaryColor: Int = 4,
    var secondaryColor: Int = 0,
    var outline: Int = 0,
    var subBackground: Int = 0,
    var subWindow: Int = 0,
    var font: Int = 0,
    var fontSize: Int = 20,
    var locale: Int = 2,

    //TimeStamps
    var timeStampsEnabled: Boolean = true,
    var useProxyForTimeStamps: Boolean = true, // can be nuked
    var showTimeStampButton: Boolean = true,
    var useAlternativeTimestampProvider: Boolean = true,

    //Auto
    var autoSkipOPED: Boolean = false,
    var autoPlay: Boolean = true,
    var autoSkipFiller: Boolean = false,

    //Update Progress
    var askIndividual: Boolean = true,
    var updateForH: Boolean = false,
    var watchPercentage: Float = 0.8f,

    //Behaviour
    var alwaysContinue: Boolean = true,
    var focusPause: Boolean = true,
    var gestures: Boolean = true,  // nuke
    var verticalSwipe: Boolean = true,
    var doubleTap: Boolean = true, //nuke this
    var fastforward: Boolean = true, // nuke this
    var seekTime: Int = 10,
    var skipTime: Int = 85,

) : Serializable