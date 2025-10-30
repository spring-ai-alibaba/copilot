import i18n from "i18next";
import {initReactI18next} from "react-i18next";
import en from "../locale/en.json";
import zh from "../locale/zh.json";

const resources = {
  en: {
    translation: {
      ...en,
    },
  },
  zh: {
    translation: {
      ...zh,
    },
  },
};

i18n.use(initReactI18next).init({
  resources,
  lng: "zh",

  interpolation: {
    escapeValue: false,
  },
});

export default i18n;
