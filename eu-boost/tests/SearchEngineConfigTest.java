package com.ux5.hyperos3.perappcn;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;
public final class SearchEngineConfigTest {
    public static void main(String[] args) throws Exception {
        for (int variant=0;variant<2;variant++) {
            boolean phone = variant==0;
            String source = new String(Files.readAllBytes(Paths.get(args[variant])), StandardCharsets.UTF_8);
            JSONObject before = new JSONObject(source);
            String extended = SearchEngineConfig.extend(source, phone);
            JSONObject after = new JSONObject(extended);
            JSONObject bd=before.getJSONObject("data"), ad=after.getJSONObject("data");
            JSONArray b, a;
            if(phone) {
                b=bd.getJSONObject("searchEngineSceneMap").getJSONObject("globalSearchSearchBox").getJSONArray("searchEngines");
                a=ad.getJSONObject("searchEngineSceneMap").getJSONObject("globalSearchSearchBox").getJSONArray("searchEngines");
                check(bd.getJSONObject("defaultSearchEngineMap").toString().equals(ad.getJSONObject("defaultSearchEngineMap").toString()),"native default changed");
                check(bd.getJSONObject("searchEngineSceneMap").getJSONObject("globalSearchHotList").toString().equals(ad.getJSONObject("searchEngineSceneMap").getJSONObject("globalSearchHotList").toString()),"hot list changed");
            } else { b=bd.getJSONArray("searchEngines");a=ad.getJSONArray("searchEngines"); }
            check(a.length()==b.length()+1,"missing Google or old engine removed");
            for(int i=0;i<b.length();i++)check(b.getJSONObject(i).toString().equals(a.getJSONObject(i).toString()),"existing engine modified");
            JSONObject google=a.getJSONObject(a.length()-1);
            check("google".equals(google.getString(phone?"searchEngineName":"label")),"label");
            String url=google.getString(phone?"searchUrl":"searchUriDefault");
            check(url.equals("https://www.google.com/search?q={searchTerms}"),"URL");
            check(extended.equals(SearchEngineConfig.extend(extended,phone)),"duplicate Google after reload");
            System.out.println((phone?"phone":"fold")+": native engines/defaults preserved; Google appended once");
        }
        boolean rejected=false;try{SearchEngineConfig.extend("{\"data\":{}}",true);}catch(Exception e){rejected=true;}
        check(rejected,"unknown schema accepted");System.out.println("GOOGLE_CONFIG_TESTS_PASS");
    }
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
