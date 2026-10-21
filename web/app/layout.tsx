import type { Metadata } from "next";
import Script from "next/script";
import "./globals.css";

export const metadata: Metadata = {
  title: "Чат",
  description: "Личный чат",
};

/**
 * Корневой layout веб-клиента.
 */
export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ru">
      <body>
        <Script id="chat-audio-unlock" strategy="beforeInteractive">
          {`(function(){function arm(){try{var A=window.AudioContext||window.webkitAudioContext;if(!A)return;if(!window.__chatAc)window.__chatAc=new A();window.__chatAc.resume();}catch(e){}}["pointerdown","mousedown","keydown","touchstart","click"].forEach(function(t){window.addEventListener(t,arm,true);})})();`}
        </Script>
        {children}
      </body>
    </html>
  );
}
