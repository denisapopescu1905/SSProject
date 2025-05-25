# Folosim un JDK oficial (11 e bine pentru Android)
FROM openjdk:11-jdk

# Setăm variabila Android SDK root
ENV ANDROID_SDK_ROOT /sdk

# Instalăm Android Command Line Tools (SDK tools)
RUN mkdir -p /sdk/cmdline-tools && \
    curl -o /sdk/cmdline-tools/cmdline-tools.zip https://dl.google.com/android/repository/commandlinetools-linux-9477386_latest.zip && \
    unzip /sdk/cmdline-tools/cmdline-tools.zip -d /sdk/cmdline-tools/ && \
    rm /sdk/cmdline-tools/cmdline-tools.zip && \
    mv /sdk/cmdline-tools/cmdline-tools /sdk/cmdline-tools/latest

ENV PATH $PATH:/sdk/cmdline-tools/latest/bin:/sdk/platform-tools

# Acceptăm licențele SDK Android automat
RUN yes | sdkmanager --licenses

# Instalăm platform tools, platforma Android 33 și build tools (ajustează la versiunea ta)
RUN sdkmanager "platform-tools" "platforms;android-33" "build-tools;33.0.2"

# Setăm directorul de lucru (în container)
WORKDIR /app

# Copiem tot proiectul în container (poți ajusta dacă ai fișiere .gitignore etc)
COPY . /app

# Comanda implicită: build debug APK
CMD ["./gradlew", "assembleDebug"]
