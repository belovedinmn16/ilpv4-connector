package org.interledger.connector.server.spring.settings.properties;

import org.interledger.connector.settings.ConnectorKey;

import java.util.Objects;

public class ConnectorKeyFromPropertyFile implements ConnectorKey {

  private String alias;

  private String version;

  public String alias() {
    return alias;
  }

  public void setAlias(String alias) {
    this.alias = alias;
  }

  public String version() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || !(o instanceof ConnectorKey)) {
      return false;
    }
    ConnectorKey that = (ConnectorKey) o;
    return alias.equals(that.alias()) &&
      version.equals(that.version());
  }

  @Override
  public int hashCode() {
    return Objects.hash(alias, version);
  }

  @Override
  public String toString() {
    return "ConnectorKeyFromPropertyFile{" +
      "alias='" + alias + '\'' +
      ", version='" + version + '\'' +
      '}';
  }
}