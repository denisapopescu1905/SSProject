# Imagine de bază cu JDK + SDK Android
FROM openjdk:17-slim

# Variabile de mediu
ENV ANDROID_HOME=/opt/android-sdk
ENV PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator

# Instalează dependințe
RUN apt-get update && apt-get install -y \
    wget unzip git curl lib32stdc++6 lib32z1 libc6-i386 \
    && rm -rf /var/lib/apt/lists/*

# Instalează Android SDK Command Line Tools
RUN mkdir -p $ANDROID_HOME/cmdline-tools && \
    cd $ANDROID_HOME/cmdline-tools && \
    wget https://dl.google.com/android/repository/commandlinetools-linux-9477386_latest.zip -O tools.zip && \
    unzip tools.zip && \
    mv cmdline-tools latest && \
    rm tools.zip

# Acceptă licențele + instalează platforma necesară
RUN yes | sdkmanager --licenses && \
    yes | sdkmanager "platforms;android-33" "build-tools;33.0.0" "platform-tools"

# Copiază fișierele proiectului
WORKDIR /app
COPY . .

# Adaugă local.properties
RUN echo "sdk.dir=$ANDROID_HOME" > local.properties

# Dă permisiune la gradlew
RUN chmod +x ./gradlew

# Rulează build-ul
RUN ./gradlew assembleDebug
