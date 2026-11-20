package pl.tvplayer;

import androidx.media3.common.PlaybackException;
import androidx.media3.datasource.HttpDataSource;

@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class AndroidPlaybackErrors {
    static int status(Throwable error) {
        for(Throwable cause=error;cause!=null;cause=cause.getCause()) if(cause instanceof HttpDataSource.InvalidResponseCodeException response) return response.responseCode;
        return 0;
    }
    static String message(PlaybackException error) {
        int code=status(error); String text;
        switch(code) {
            case 401: text="Dostawca odrzucił dane dostępu. Sprawdź konto lub odśwież źródło."; break;
            case 403: text="Dostawca odmówił dostępu do tego materiału. Sprawdź ważność konta i odśwież źródło."; break;
            case 404: case 410: text="Ten adres filmu jest niedostępny lub wygasł. Odśwież ofertę dostawcy."; break;
            case 416: text="Serwer odrzucił zapisaną pozycję filmu. Spróbuj odtworzyć materiał od początku."; break;
            case 429: case 458: text="Dostawca ograniczył liczbę żądań lub jednoczesnych odtworzeń. Zamknij inne odtwarzanie i spróbuj ponownie."; break;
            default: text=code>=500?"Serwer dostawcy jest niedostępny. Spróbuj ponownie później.":"Nie można odtworzyć materiału. Sprawdź połączenie i dostępność filmu u dostawcy.";
        }
        return code==0?text:text+"\nHTTP "+code;
    }
}
