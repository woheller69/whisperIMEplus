/*
 * Copyright 2016 Luca Martino.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copyFile of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.whisperonnx.voice_translation.neural_networks.voice;


import com.whisperonnx.voice_translation.neural_networks.NeuralNetworkApiListener;

public interface RecognizerMultiListener extends NeuralNetworkApiListener {
    void onSpeechRecognizedResult(String text1, String languageCode1, double confidenceScore1, String text2, String languageCode2, double confidenceScore2);
}
