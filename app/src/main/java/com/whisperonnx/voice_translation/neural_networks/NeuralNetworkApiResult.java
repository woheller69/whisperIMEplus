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

package com.whisperonnx.voice_translation.neural_networks;


public class NeuralNetworkApiResult extends NeuralNetworkApiText {
    private double confidenceScore=0;
    private boolean isFinal;

    public NeuralNetworkApiResult(String text, double confidenceScore, boolean isFinal){
        super(text);
        this.confidenceScore=confidenceScore;
        this.isFinal=isFinal;
    }

    public NeuralNetworkApiResult(String text, boolean isFinal){
        super(text);
        this.isFinal=isFinal;
    }

    public NeuralNetworkApiResult(String text){
        super(text);
    }

    public double getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(float confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public boolean isFinal() {
        return isFinal;
    }

    public void setFinal(boolean aFinal) {
        isFinal = aFinal;
    }
}
