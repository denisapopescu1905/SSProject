# Imagine de bază cu JDK 17
FROM openjdk:17-slim

# Instalează utilitare necesare
RUN apt-get update && apt-get install -y wget unzip

# Setează calea SDK-ului Android
ENV ANDROID_SDK_ROOT /sdk

# Creează folder SDK
RUN mkdir -p $ANDROID_SDK_ROOT/cmdline-tools

# Descarcă Android command line tools
RUN wget https://dl.google.com/android/repository/commandlinetools-linux-9477386_latest.zip -O /tmp/cmdline-tools.zip && \
    unzip /tmp/cmdline-tools.zip -d $ANDROID_SDK_ROOT/cmdline-tools && \
    rm /tmp/cmdline-tools.zip

# Mută folderul commandlinetools în locația corectă (cerută de SDK)
RUN mkdir -p $ANDROID_SDK_ROOT/cmdline-tools/latest && \
    mv $ANDROID_SDK_ROOT/cmdline-tools/cmdline-tools/* $ANDROID_SDK_ROOT/cmdline-tools/latest/

# Adaugă SDK tools în PATH
ENV PATH $PATH:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools

# Acceptă licențele automat
RUN yes | sdkmanager --licenses

# Instalează platform-tools și SDK Build tools pentru Android 33 (poți schimba versiunea aici)
RUN sdkmanager "platform-tools" "platforms;android-33" "build-tools;33.0.0"

# Setează directorul de lucru în container
WORKDIR /app

# Copiază codul sursă în container
COPY . .

# Rulează build-ul cu Gradle Wrapper
RUN chmod +x ./gradlew
RUN ./gradlew assembleDebug
