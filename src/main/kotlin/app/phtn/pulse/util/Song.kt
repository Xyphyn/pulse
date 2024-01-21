package app.phtn.pulse.util
import dev.emortal.nbstom.NBS
import dev.emortal.nbstom.NBSSong
import java.nio.file.Path

object SongManager {
    val badApple = NBSSong(Path.of((this::class.java.getResource("/songs/gourmet.nbs") ?: throw Error("oops")).toURI()))
}