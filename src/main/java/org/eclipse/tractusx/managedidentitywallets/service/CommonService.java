/*
 * *******************************************************************************
 *  Copyright (c) 2021,2023 Contributors to the Eclipse Foundation
 *
 *  See the NOTICE file(s) distributed with this work for additional
 *  information regarding copyright ownership.
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0.
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *  License for the specific language governing permissions and limitations
 *  under the License.
 *
 *  SPDX-License-Identifier: Apache-2.0
 * ******************************************************************************
 */

package org.eclipse.tractusx.managedidentitywallets.service;

import com.apicatalog.jsonld.JsonLdError;
import com.apicatalog.jsonld.document.Document;
import com.apicatalog.jsonld.document.JsonDocument;
import com.apicatalog.jsonld.loader.DocumentLoaderOptions;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringEscapeUtils;
import org.eclipse.tractusx.managedidentitywallets.config.MIWSettings;
import org.eclipse.tractusx.managedidentitywallets.constant.StringPool;
import org.eclipse.tractusx.managedidentitywallets.dao.entity.Wallet;
import org.eclipse.tractusx.managedidentitywallets.dao.repository.WalletRepository;
import org.eclipse.tractusx.managedidentitywallets.exception.WalletNotFoundProblem;
import org.eclipse.tractusx.managedidentitywallets.utils.CommonUtils;
import org.eclipse.tractusx.managedidentitywallets.utils.Validate;
import org.eclipse.tractusx.ssi.lib.exception.DidParseException;
import org.eclipse.tractusx.ssi.lib.model.RemoteDocumentLoader;
import org.eclipse.tractusx.ssi.lib.model.verifiable.credential.VerifiableCredential;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class CommonService {

    private final WalletRepository walletRepository;

    private final MIWSettings settings;

    /**
     * bean initializaation makes sure to populate the
     * json remote document loader with cached documents
     *
     * @throws JsonLdError
     * @throws URISyntaxException
     */
    @PostConstruct
    public void initialize() throws JsonLdError, URISyntaxException {
        log.info("Initializing Json LD RemoteDocumentLoader cache");
        var loader = RemoteDocumentLoader.getInstance();
        boolean isHttpsEnabled = loader.isEnableHttps();
        boolean isHttpEnabled = loader.isEnableHttp();
        boolean isFileEnabled = loader.isEnableFile();
        var cache = loader.getLocalCache();
        DocumentLoaderOptions options = new DocumentLoaderOptions();
        DocumentLoaderOptions loaderOptions = new DocumentLoaderOptions();
        loaderOptions.setProfile("http://www.w3.org/ns/json-ld#context");
        loaderOptions.setRequestProfile(Arrays.asList(loaderOptions.getProfile()));
        String pwd = Paths.get("").toAbsolutePath().toString().replaceAll(" ", "%20");
        for (Map.Entry<String, String> cacheDocument : settings.contextMappings().entrySet()) {
            String source = cacheDocument.getKey();
            if (cache.get(source) == null) {
                String target = cacheDocument.getValue();
                target = target.replace("%PWD%", pwd);
                log.debug(String.format("Trying to preload json ld schema %s", target));
                URI targetUri = new URI(target);
                if (targetUri.getScheme().startsWith("https")) {
                    loader.setEnableHttps(true);
                } else if (targetUri.getScheme().startsWith("http")) {
                    loader.setEnableHttp(true);
                } else if (targetUri.getScheme().startsWith("file")) {
                    loader.setEnableFile(true);
                }
                try {
                    Document document = loader.loadDocument(targetUri, options);
                    if (document != null && document instanceof JsonDocument) {
                        log.info(String.format("Storing preloaded json ld schema %s under namespace %s into Json LD RemoteDocumentLoader cache", target, source));
                        cache.put(new URI(source), (JsonDocument) document);
                    }
                } catch (IllegalArgumentException e) {
                    log.warn(String.format("Could not populate Json LD RemoteDocumentLoader cache for namespace %s because of %s", target, e), e);
                }
            } else {
                log.debug(String.format("Json LD RemoteDocumentLoader cache already contains an entry for %s", source));
            }
        }
        // restore previous configs
        loader.setEnableHttps(isHttpsEnabled);
        loader.setEnableHttp(isHttpEnabled);
        loader.setEnableFile(isFileEnabled);
    }

    /**
     * Gets wallet by identifier(BPN or did).
     *
     * @param identifier the identifier
     * @return the wallet by identifier
     */
    public Wallet getWalletByIdentifier(String identifier) {
        Wallet wallet;
        if (CommonUtils.getIdentifierType(identifier).equals(StringPool.BPN)) {
            wallet = walletRepository.getByBpn(identifier);
        } else {
            try {
                wallet = walletRepository.getByDid(identifier);
            } catch (DidParseException e) {
                log.error("Error while parsing did {}", StringEscapeUtils.escapeJava(identifier), e);
                throw new WalletNotFoundProblem("Error while parsing did " + identifier);
            }
        }
        Validate.isNull(wallet).launch(new WalletNotFoundProblem("Wallet not found for identifier " + identifier));
        return wallet;
    }

    public static boolean validateExpiry(boolean withCredentialExpiryDate, VerifiableCredential verifiableCredential, Map<String, Object> response) {
        //validate expiry date
        boolean dateValidation = true;
        if (withCredentialExpiryDate) {
            Instant expirationDate = verifiableCredential.getExpirationDate();
            if (expirationDate.isBefore(Instant.now())) {
                dateValidation = false;
                response.put(StringPool.VALIDATE_EXPIRY_DATE, false);
            } else {
                response.put(StringPool.VALIDATE_EXPIRY_DATE, true);
            }
        }
        return dateValidation;
    }

}
