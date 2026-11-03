package com.softsecure.id;

import com.softsecure.id.config.AppConfig;
import com.softsecure.id.export.CardRenderer;
import com.softsecure.id.export.ExportService;
import com.softsecure.id.export.QrService;
import com.softsecure.id.persistence.Database;
import com.softsecure.id.persistence.RequestRepository;
import com.softsecure.id.persistence.UserRepository;
import com.softsecure.id.security.AuthService;
import com.softsecure.id.security.Crypto;
import com.softsecure.id.security.PasswordHasher;
import com.softsecure.id.service.IdService;

/** Explicit composition root; no global mutable service locator or framework. */
public record AppServices(AppConfig config, Database database, Crypto crypto, PasswordHasher passwords,
                          UserRepository users, AuthService auth, IdService ids, QrService qr,
                          CardRenderer renderer, ExportService exports) {
    public static AppServices load() throws Exception {
        AppConfig config = AppConfig.load();
        Database database = new Database(config);
        Crypto crypto = new Crypto(config);
        PasswordHasher passwords = new PasswordHasher();
        UserRepository users = new UserRepository(crypto);
        AuthService auth = new AuthService(database, users, passwords, config);
        IdService ids = new IdService(database, users, new RequestRepository(crypto), crypto, config);
        QrService qr = new QrService();
        CardRenderer renderer = new CardRenderer(qr, config);
        return new AppServices(config, database, crypto, passwords, users, auth, ids, qr, renderer,
                new ExportService(ids, renderer));
    }
}
