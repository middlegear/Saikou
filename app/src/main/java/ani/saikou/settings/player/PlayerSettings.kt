package ani.saikou.settings.player

import java.io.Serializable

data class PlayerSettings(

    //TimeStamps
    var timeStampsEnabled: Boolean = true,
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
    var customLoadingScreen :Boolean = true,

//    Player gestures
    var verticalSwipe: Boolean = true,
    var doubleTap: Boolean = true,
    var holdToFastForward: Boolean = true,
    var seekTime: Int = 10,
    var skipTime: Int = 85,

) : Serializable