CREATE TABLE AccessRulesData (
    pK INTEGER NOT NULL,
    accessRule VARCHAR(254) NOT NULL,
    isRecursive SMALLINT NOT NULL,
    rowProtection CLOB(10K),
    rowVersion INTEGER NOT NULL,
    rule INTEGER NOT NULL,
    AdminGroupData_accessRules INTEGER,
    PRIMARY KEY (pK)
);

CREATE TABLE AdminEntityData (
    pK INTEGER NOT NULL,
    cAId INTEGER NOT NULL,
    matchType INTEGER NOT NULL,
    matchValue VARCHAR(254),
    matchWith INTEGER NOT NULL,
    rowProtection CLOB(10K),
    rowVersion INTEGER NOT NULL,
    AdminGroupData_adminEntities INTEGER,
    PRIMARY KEY (pK)
);

CREATE TABLE AdminGroupData (
    pK INTEGER NOT NULL,
    adminGroupName VARCHAR(254) NOT NULL,
    cAId INTEGER NOT NULL,
    rowProtection CLOB(10K),
    rowVersion INTEGER NOT NULL,
    PRIMARY KEY (pK)
);

CREATE TABLE AdminPreferencesData (
    id VARCHAR(254) NOT NULL,
    data BLOB(200K) NOT NULL,
    rowProtection CLOB(10K),
    rowVersion INTEGER NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE ApprovalData (
    id INTEGER NOT NULL,
    approvalData CLOB NOT NULL,
    approvalId INTEGER NOT NULL,
    approvalType INTEGER NOT NULL,
    cAId INTEGER NOT NULL,
    endEntityProfileId INTEGER NOT NULL,
    expireDate BIGINT NOT NULL,
    remainingApprovals INTEGER NOT NULL,
    reqAdminCertIssuerDn VARCHAR(254),
    reqAdminCertSn VARCHAR(254),
    requestData CLOB NOT NULL,
    requestDate BIGINT NOT NULL,
    rowProtection CLOB(10K),
    rowVersion INTEGER NOT NULL,
    status INTEGER NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE AuthorizationTreeUpdateData (
    pK INTEGER NOT NULL,
    authorizationTreeUpdateNumber INTEGER NOT NULL,
    rowProtection CLOB(10K),
    rowVersion INTEGER NOT NULL,
    PRIMARY KEY (pK)
);

CREATE TABLE CAData (
    cAId INTEGER NOT NULL,
    data CLOB NOT NULL,
    expireTime BIGINT NOT NULL,
    name VARCHAR(254),
    rowProtection CLOB(10K),
    rowVersion INTEGER NOT NULL,
    status INTEGER NOT NULL,
    subjectDN VARCHAR(254),
    updateTime BIGINT NOT NULL,
    PRIMARY KEY (cAId)
);

CREATE TABLE CRLData (
    fingerprint VARCHAR(254) NOT NULL,
    base64Crl CLOB NOT NULL,
    cAFingerprint VARCHAR(254) NOT NULL,
    cRLNumber INTEGER NOT NULL,
    deltaCRLIndicator INTEGER NOT NULL,
    issuerDN VARCHAR(254) NOT NULL,
    nextUpdate BIGINT NOT NULL,
    rowProtection CLOB(10K),
    rowVersion INTEGER NOT NULL,
    thisUpdate BIGINT NOT NULL,
    PRIMARY KEY (fingerprint)
);

CREATE TABLE CertReqHistoryData (
    fingerprint VARCHAR(254) NOT NULL,
    issuerDN VARCHAR(254) NOT NULL,
    rowProtection CLOB(10K),
    rowVersion INTEGER NOT NULL,
    serialNumber VARCHAR(254) NOT NULL,
    timestamp BIGINT NOT NULL,
    userDataVO CLOB NOT NULL,
    username VARCHAR(254) NOT NULL,
    PRIMARY KEY (fingerprint)
);

CREATE TABLE CertificateData (
    fingerprint VARCHAR(254) NOT NULL,
    base64Cert CLOB,
    cAFingerprint VARCHAR(254),
    certificateProfileId INTEGER NOT NULL,
    expireDate BIGINT NOT NULL,
    issuerDN VARCHAR(254) NOT NULL,
    revocationDate BIGINT NOT NULL,
    revocationReason INTEGER NOT NULL,
    rowProtection CLOB(10K),
    rowVersion INTEGER NOT NULL,
    serialNumber VARCHAR(254) NOT NULL,
    status INTEGER NOT NULL,
    subjectDN VARCHAR(254) NOT NULL,
    subjectKeyId VARCHAR(254),
    tag VARCHAR(254),
    type INTEGER NOT NULL,
    updateTime BIGINT NOT NULL,
    username VARCHAR(254),
    PRIMARY KEY (fingerprint)
);

CREATE TABLE Base64CertData (
    fingerprint VARCHAR(254) NOT NULL,
    base64Cert CLOB,
    rowProtection CLOB(10K),
    rowVersion INTEGER NOT NULL,
    PRIMARY KEY (fingerprint)
);

CREATE TABLE CertificateProfileData (
    id INTEGER NOT NULL,
    certificateProfileName VARCHAR(254) NOT NULL,
    data BLOB(1M) NOT NULL,
    rowProtection CLOB(10K),
    rowVersion INTEGER NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE EndEntityProfileData (
    id INTEGER NOT NULL,
    data BLOB(1M) NOT NULL,
    profileName VARCHAR(254) NOT NULL,
    rowProtection CLOB(10K),
    rowVersion INTEGER NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE GlobalConfigurationData (
    configurationId VARCHAR(254) NOT NULL,
    data BLOB(200K) NOT NULL,
    rowProtection CLOB(10K),
    rowVersion INTEGER NOT NULL,
    PRIMARY KEY (configurationId)
);

CREATE TABLE HardTokenCertificateMap (
    certificateFingerprint VARCHAR(254) NOT NULL,
    rowProtection CLOB(10K),
    rowVersion INTEGER NOT NULL,
    tokenSN VARCHAR(254) NOT NULL,
    PRIMARY KEY (certificateFingerprint)
);

CREATE TABLE HardTokenData (
    tokenSN VARCHAR(254) NOT NULL,
    cTime BIGINT NOT NULL,
    data BLOB(200K),
    mTime BIGINT NOT NULL,
    rowProtection CLOB(10K),
    rowVersion INTEGER NOT NULL,
    significantIssuerDN VARCHAR(254),
    tokenType INTEGER NOT NULL,
    username VARCHAR(254),
    PRIMARY KEY (tokenSN)
);

CREATE TABLE HardTokenIssuerData (
    id INTEGER NOT NULL,
    adminGroupId INTEGER NOT NULL,
    alias VARCHAR(254) NOT NULL,
    data BLOB(200K) NOT NULL,
    rowProtection CLOB(10K),
    rowVersion INTEGER NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE HardTokenProfileData (
    id INTEGER NOT NULL,
    data CLOB,
    name VARCHAR(254) NOT NULL,
    rowProtection CLOB(10K),
    rowVersion INTEGER NOT NULL,
    updateCounter INTEGER NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE HardTokenPropertyData (
    id VARCHAR(80) NOT NULL,
    property VARCHAR(254) NOT NULL,
    rowProtection CLOB(10K),
    rowVersion INTEGER NOT NULL,
    value VARCHAR(254),
    PRIMARY KEY (id,
    property)
);

CREATE TABLE KeyRecoveryData (
    certSN VARCHAR(254) NOT NULL,
    issuerDN VARCHAR(254) NOT NULL,
    keyData CLOB NOT NULL,
    markedAsRecoverable SMALLINT NOT NULL,
    rowProtection CLOB(10K),
    rowVersion INTEGER NOT NULL,
    username VARCHAR(254),
    PRIMARY KEY (certSN,
    issuerDN)
);

CREATE TABLE LogConfigurationData (
    id INTEGER NOT NULL,
    logConfiguration BLOB(200K) NOT NULL,
    logEntryRowNumber INTEGER NOT NULL,
    rowProtection CLOB(10K),
    rowVersion INTEGER NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE LogEntryData (
    id INTEGER NOT NULL,
    adminData VARCHAR(254),
    adminType INTEGER NOT NULL,
    cAId INTEGER NOT NULL,
    certificateSNR VARCHAR(254),
    event INTEGER NOT NULL,
    logComment VARCHAR(254),
    module INTEGER NOT NULL,
    rowProtection CLOB(10K),
    rowVersion INTEGER NOT NULL,
    time BIGINT NOT NULL,
    username VARCHAR(254),
    PRIMARY KEY (id)
);

CREATE TABLE PublisherData (
    id INTEGER NOT NULL,
    data CLOB,
    name VARCHAR(254),
    rowProtection CLOB(10K),
    rowVersion INTEGER NOT NULL,
    updateCounter INTEGER NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE PublisherQueueData (
    pk VARCHAR(254) NOT NULL,
    fingerprint VARCHAR(254),
    lastUpdate BIGINT NOT NULL,
    publishStatus INTEGER NOT NULL,
    publishType INTEGER NOT NULL,
    publisherId INTEGER NOT NULL,
    rowProtection CLOB(10K),
    rowVersion INTEGER NOT NULL,
    timeCreated BIGINT NOT NULL,
    tryCounter INTEGER NOT NULL,
    volatileData CLOB,
    PRIMARY KEY (pk)
);

CREATE TABLE ServiceData (
    id INTEGER NOT NULL,
    data CLOB,
    name VARCHAR(254) NOT NULL,
    nextRunTimeStamp BIGINT NOT NULL,
    rowProtection CLOB(10K),
    rowVersion INTEGER NOT NULL,
    runTimeStamp BIGINT NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE UserData (
    username VARCHAR(254) NOT NULL,
    cAId INTEGER NOT NULL,
    cardNumber VARCHAR(254),
    certificateProfileId INTEGER NOT NULL,
    clearPassword VARCHAR(254),
    endEntityProfileId INTEGER NOT NULL,
    extendedInformationData CLOB,
    hardTokenIssuerId INTEGER NOT NULL,
    keyStorePassword VARCHAR(254),
    passwordHash VARCHAR(254),
    rowProtection CLOB(10K),
    rowVersion INTEGER NOT NULL,
    status INTEGER NOT NULL,
    subjectAltName VARCHAR(254),
    subjectDN VARCHAR(254),
    subjectEmail VARCHAR(254),
    timeCreated BIGINT NOT NULL,
    timeModified BIGINT NOT NULL,
    tokenType INTEGER NOT NULL,
    type INTEGER NOT NULL,
    PRIMARY KEY (username)
);

CREATE TABLE UserDataSourceData (
    id INTEGER NOT NULL,
    data CLOB,
    name VARCHAR(254) NOT NULL,
    rowProtection CLOB(10K),
    rowVersion INTEGER NOT NULL,
    updateCounter INTEGER NOT NULL,
    PRIMARY KEY (id)
);

alter table AccessRulesData add constraint FKABB4C1DFD8AEA20 foreign key (AdminGroupData_accessRules) references AdminGroupData;

alter table AdminEntityData add constraint FKD9A99EBCB370315D foreign key (AdminGroupData_adminEntities) references AdminGroupData;

