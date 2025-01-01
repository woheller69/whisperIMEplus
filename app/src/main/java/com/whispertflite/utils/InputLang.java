package com.whispertflite.utils;

import java.util.ArrayList;

public class InputLang {
    String code;
    long id;

    private InputLang(String code, long id) {
        this.code = code;
        this.id = id;
    }

    // Initialize the list of input language objects
    public static ArrayList<InputLang> getLangList() {
        ArrayList<InputLang> inputLangList = new ArrayList<>();
        inputLangList.add(new InputLang("en",50259));
        inputLangList.add(new InputLang("zh",50260));
        inputLangList.add(new InputLang("de",50261));
        inputLangList.add(new InputLang("es",50262));
        inputLangList.add(new InputLang("ru",50263));
        inputLangList.add(new InputLang("ko",50264));
        inputLangList.add(new InputLang("fr",50265));
        inputLangList.add(new InputLang("ja",50266));
        inputLangList.add(new InputLang("pt",50267));
        inputLangList.add(new InputLang("tr",50268));
        inputLangList.add(new InputLang("pl",50269));
        inputLangList.add(new InputLang("ca",50270));
        inputLangList.add(new InputLang("nl",50271));
        inputLangList.add(new InputLang("ar",50272));
        inputLangList.add(new InputLang("sv",50273));
        inputLangList.add(new InputLang("it",50274));
        inputLangList.add(new InputLang("id",50275));
        inputLangList.add(new InputLang("hi",50276));
        inputLangList.add(new InputLang("fi",50277));
        inputLangList.add(new InputLang("vi",50278));
        inputLangList.add(new InputLang("he",50279));
        inputLangList.add(new InputLang("uk",50280));
        inputLangList.add(new InputLang("el",50281));
        inputLangList.add(new InputLang("ms",50282));
        inputLangList.add(new InputLang("cs",50283));
        inputLangList.add(new InputLang("ro",50284));
        inputLangList.add(new InputLang("da",50285));
        inputLangList.add(new InputLang("hu",50286));
        inputLangList.add(new InputLang("ta",50287));
        inputLangList.add(new InputLang("no",50288));
        inputLangList.add(new InputLang("th",50289));
        inputLangList.add(new InputLang("ur",50290));
        inputLangList.add(new InputLang("hr",50291));
        inputLangList.add(new InputLang("bg",50292));
        inputLangList.add(new InputLang("lt",50293));
        inputLangList.add(new InputLang("la",50294));
        inputLangList.add(new InputLang("mi",50295));
        inputLangList.add(new InputLang("ml",50296));
        inputLangList.add(new InputLang("cy",50297));
        inputLangList.add(new InputLang("sk",50298));
        inputLangList.add(new InputLang("te",50299));
        inputLangList.add(new InputLang("fa",50300));
        inputLangList.add(new InputLang("lv",50301));
        inputLangList.add(new InputLang("bn",50302));
        inputLangList.add(new InputLang("sr",50303));
        inputLangList.add(new InputLang("az",50304));
        inputLangList.add(new InputLang("sl",50305));
        inputLangList.add(new InputLang("kn",50306));
        inputLangList.add(new InputLang("et",50307));
        inputLangList.add(new InputLang("mk",50308));
        inputLangList.add(new InputLang("br",50309));
        inputLangList.add(new InputLang("eu",50310));
        inputLangList.add(new InputLang("is",50311));
        inputLangList.add(new InputLang("hy",50312));
        inputLangList.add(new InputLang("ne",50313));
        inputLangList.add(new InputLang("mn",50314));
        inputLangList.add(new InputLang("bs",50315));
        inputLangList.add(new InputLang("kk",50316));
        inputLangList.add(new InputLang("sq",50317));
        inputLangList.add(new InputLang("sw",50318));
        inputLangList.add(new InputLang("gl",50319));
        inputLangList.add(new InputLang("mr",50320));
        inputLangList.add(new InputLang("pa",50321));
        inputLangList.add(new InputLang("si",50322));
        inputLangList.add(new InputLang("km",50323));
        inputLangList.add(new InputLang("sn",50324));
        inputLangList.add(new InputLang("yo",50325));
        inputLangList.add(new InputLang("so",50326));
        inputLangList.add(new InputLang("af",50327));
        inputLangList.add(new InputLang("oc",50328));
        inputLangList.add(new InputLang("ka",50329));
        inputLangList.add(new InputLang("be",50330));
        inputLangList.add(new InputLang("tg",50331));
        inputLangList.add(new InputLang("sd",50332));
        inputLangList.add(new InputLang("gu",50333));
        inputLangList.add(new InputLang("am",50334));
        inputLangList.add(new InputLang("yi",50335));
        inputLangList.add(new InputLang("lo",50336));
        inputLangList.add(new InputLang("uz",50337));
        inputLangList.add(new InputLang("fo",50338));
        inputLangList.add(new InputLang("ht",50339));
        inputLangList.add(new InputLang("ps",50340));
        inputLangList.add(new InputLang("tk",50341));
        inputLangList.add(new InputLang("nn",50342));
        inputLangList.add(new InputLang("mt",50343));
        inputLangList.add(new InputLang("sa",50344));
        inputLangList.add(new InputLang("lb",50345));
        inputLangList.add(new InputLang("my",50346));
        inputLangList.add(new InputLang("bo",50347));
        inputLangList.add(new InputLang("tl",50348));
        inputLangList.add(new InputLang("mg",50349));
        inputLangList.add(new InputLang("as",50350));
        inputLangList.add(new InputLang("tt",50351));
        inputLangList.add(new InputLang("haw",50352));
        inputLangList.add(new InputLang("ln",50353));
        inputLangList.add(new InputLang("ha",50354));
        inputLangList.add(new InputLang("ba",50355));
        inputLangList.add(new InputLang("jw",50356));
        inputLangList.add(new InputLang("su",50357));

        return inputLangList;

    }

    public static String getLanguageCodeById(ArrayList<InputLang> inputLangList, int id) {
        for (InputLang lang : inputLangList) {
            if (lang.getId() == id) {
                return lang.getCode();
            }
        }
        return "";
    }

    private long getId() {
        return id;
    }

    private String getCode() {
        return code;
    }

}
