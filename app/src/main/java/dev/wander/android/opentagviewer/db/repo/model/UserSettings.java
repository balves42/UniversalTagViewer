package dev.wander.android.opentagviewer.db.repo.model;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class UserSettings {
    private Boolean useDarkTheme;
    private String anisetteServerUrl;
    private String fmdServerUrl;
    private String fmdEmail;
    private String fmdPassword;

    private String language;
    private Boolean enableDebugData;

    public boolean hasDarkThemeEnabled() {
        return this.useDarkTheme == Boolean.TRUE;
    }

    public String getAnisetteServerUrl() {
        return anisetteServerUrl;
    }

    public String getFmdServerUrl() {
        return fmdServerUrl;
    }
    public void setFmdServerUrl(String fmdServerUrl) {
        this.fmdServerUrl = fmdServerUrl;
    }

    public void setFmdEmail(String fmdEmail) {
        this.fmdEmail = fmdEmail;
    }

    public void setFmdPassword(String fmdPassword) {
        this.fmdPassword = fmdPassword;
    }

    public String getFmdEmail() {
        return fmdEmail;
    }

    public String getFmdPassword() {
        return fmdPassword;
    }
}
