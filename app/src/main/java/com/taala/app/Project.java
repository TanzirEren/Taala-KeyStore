package com.taala.app;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

/** One keystore project. The JSON layout matches the Taala web app, so backups are interchangeable. */
public class Project {
    public String id = UUID.randomUUID().toString();
    public long created;
    public boolean pin, ok;
    public String note = "";
    public String project = "", app = "", pkg = "", alias = "", sp = "", kp = "";
    public String cn = "", ou = "", o = "", l = "", st = "", c = "";
    public int years = 25, bits = 2048;
    public String jks = "", p12 = "", cert = "", key = "", sha1 = "", sha256 = "", expires = "";

    public JSONObject toJson() throws JSONException {
        JSONObject i = new JSONObject();
        i.put("project", project).put("app", app).put("pkg", pkg).put("alias", alias)
                .put("sp", sp).put("kp", kp).put("years", years).put("bits", bits)
                .put("cn", cn).put("ou", ou).put("o", o).put("l", l).put("st", st).put("c", c);
        JSONObject j = new JSONObject();
        j.put("ok", ok).put("id", id).put("created", created).put("info", i)
                .put("jks", jks).put("p12", p12).put("cert", cert).put("key", key)
                .put("sha1", sha1).put("sha256", sha256).put("expires", expires)
                .put("pin", pin).put("note", note);
        return j;
    }

    public static Project fromJson(JSONObject j) throws JSONException {
        Project p = new Project();
        JSONObject i = j.getJSONObject("info");
        p.id = j.getString("id");
        p.created = j.optLong("created", System.currentTimeMillis());
        p.ok = j.optBoolean("ok");
        p.pin = j.optBoolean("pin");
        p.note = j.optString("note", "");
        p.jks = j.optString("jks");
        p.p12 = j.optString("p12");
        p.cert = j.optString("cert");
        p.key = j.optString("key");
        p.sha1 = j.optString("sha1");
        p.sha256 = j.optString("sha256");
        p.expires = j.optString("expires");
        p.project = i.optString("project");
        p.app = i.optString("app");
        p.pkg = i.optString("pkg");
        p.alias = i.optString("alias");
        p.sp = i.optString("sp");
        p.kp = i.optString("kp");
        p.years = i.optInt("years", 25);
        p.bits = i.optInt("bits", 2048);
        p.cn = i.optString("cn");
        p.ou = i.optString("ou");
        p.o = i.optString("o");
        p.l = i.optString("l");
        p.st = i.optString("st");
        p.c = i.optString("c");
        return p;
    }
}
