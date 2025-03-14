/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.fml.loading.moddiscovery;

import cpw.mods.jarhandling.JarMetadata;
import cpw.mods.jarhandling.SecureJar;
import net.minecraftforge.fml.loading.LogMarkers;
import net.minecraftforge.forgespi.language.IConfigurable;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.forgespi.locating.IModFile;
import net.minecraftforge.forgespi.locating.IModLocator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.jar.Manifest;

public abstract class AbstractModLocator implements IModLocator
{
    private static final   Logger LOGGER    = LogManager.getLogger();
    protected static final String MODS_TOML = "META-INF/mods.toml";
    protected static final String MANIFEST = "META-INF/MANIFEST.MF";

    protected Optional<IModFile> createMod(Path... path) {
        return this.createMod(getDefaultJarModType(), path);
    }

    // FORGE: Backporting artifact, the defaultType must be the first parameter since path is varargs
    protected Optional<IModFile> createMod(String defaultType, Path... path) {
        var mjm = new ModJarMetadata();
        var sj = SecureJar.from(
                Manifest::new,
                jar -> jar.findFile(MODS_TOML).isPresent() ? mjm : JarMetadata.from(jar, path),
                (root, p) -> true,
                path
        );

        IModFile mod;
        var type = sj.getManifest().getMainAttributes().getValue(ModFile.TYPE);
        if (type == null) {
            type = defaultType;
        }
        if (sj.findFile(MODS_TOML).isPresent()) {
            LOGGER.debug(LogMarkers.SCAN, "Found {} mod of type {}: {}", MODS_TOML, type, path);
            mod = new ModFile(sj, this, ModFileParser::modsTomlParser);
        } else if (type != null) {
            LOGGER.debug(LogMarkers.SCAN, "Found {} mod of type {}: {}", MANIFEST, type, path);
            mod = new ModFile(sj, this, this::manifestParser, type);
        } else {
            return Optional.empty();
        }

        mjm.setModFile(mod);
        return Optional.of(mod);
    }

    protected IModFileInfo manifestParser(final IModFile mod) {
        Function<String, Optional<String>> cfg = name -> Optional.ofNullable(mod.getSecureJar().getManifest().getMainAttributes().getValue(name));
        var license = cfg.apply("LICENSE").orElse("");
        var dummy = new IConfigurable() {
            @Override
            public <T> Optional<T> getConfigElement(String... key) {
                return Optional.empty();
            }
            @Override
            public List<? extends IConfigurable> getConfigList(String... key) {
                return Collections.emptyList();
            }
        };

        return new DefaultModFileInfo(mod, license, dummy);
    }

    @Override
    public boolean isValid(final IModFile modFile) {
        return true;
    }

    protected String getDefaultJarModType() {
        return null;
    }

    private record DefaultModFileInfo(IModFile mod, String license, IConfigurable configurable) implements IModFileInfo, IConfigurable {
        @Override
        public <T> Optional<T> getConfigElement(final String... strings)
        {
            return Optional.empty();
        }

        @Override
        public List<? extends IConfigurable> getConfigList(final String... strings)
        {
            return null;
        }

        @Override public List<IModInfo> getMods() { return Collections.emptyList(); }
        @Override public List<LanguageSpec> requiredLanguageLoaders() { return Collections.emptyList(); }
        @Override public boolean showAsResourcePack() { return false; }
        @Override public Map<String, Object> getFileProperties() { return Collections.emptyMap(); }
        @Override
        public String getLicense() { return license; }

        @Override public IModFile getFile() { return mod; }
        @Override public IConfigurable getConfig() { return configurable; }

        // These Should never be called as it's only called from ModJarMetadata.version and we bypass that
        @Override public String moduleName() { return mod.getSecureJar().name(); }
        @Override public String versionString() { return null; }
        @Override public List<String> usesServices() { return null; }

        @Override
        public String toString() {
            return "IModFileInfo(" + mod.getFilePath() + ")";
        }
    }
}
