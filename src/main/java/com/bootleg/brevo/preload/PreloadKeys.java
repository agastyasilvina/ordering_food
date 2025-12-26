package com.bootleg.brevo.preload;

public final class PreloadKeys {
  private PreloadKeys() {
  }

  public static String groupKey(String journeyCode, int groupNo) {
    return journeyCode + "|" + groupNo;
  }

  public static String fieldKey(String formCode, String fieldCode) {
    return formCode + "|" + fieldCode;
  }
}
