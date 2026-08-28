import { Injectable } from '@angular/core';
import { Observable, Subject } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class LiveAudioService {
  private audioContext: AudioContext | null = null;
  private mediaStream: MediaStream | null = null;
  private sourceNode: MediaStreamAudioSourceNode | null = null;
  private processorNode: ScriptProcessorNode | null = null;
  private nextPlayTime = 0;
  private isRecording = false;

  private readonly pcmData$ = new Subject<ArrayBuffer>();

  get pcmChunks$(): Observable<ArrayBuffer> {
    return this.pcmData$.asObservable();
  }

  async startRecording(): Promise<void> {
    if (this.isRecording) {
      return;
    }

    if (!navigator?.mediaDevices?.getUserMedia) {
      throw new Error('Microphone recording is not supported in this browser.');
    }

    this.mediaStream = await navigator.mediaDevices.getUserMedia({
      audio: {
        channelCount: 1,
        sampleRate: 16000,
        echoCancellation: true,
        noiseSuppression: true,
        autoGainControl: true,
      },
    });

    const AudioCtx = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
    this.audioContext = new AudioCtx({ sampleRate: 16000 });
    this.sourceNode = this.audioContext.createMediaStreamSource(this.mediaStream);

    // Buffer size 2048 at 16kHz is ~128ms of audio per chunk
    this.processorNode = this.audioContext.createScriptProcessor(2048, 1, 1);
    this.processorNode.onaudioprocess = (event: AudioProcessingEvent) => {
      if (!this.isRecording) {
        return;
      }
      const inputData = event.inputBuffer.getChannelData(0);
      const pcm16 = this.floatTo16BitPCM(inputData);
      this.pcmData$.next(pcm16);
    };

    this.sourceNode.connect(this.processorNode);
    this.processorNode.connect(this.audioContext.destination);
    this.isRecording = true;
  }

  stopRecording(): void {
    this.isRecording = false;

    if (this.processorNode) {
      this.processorNode.disconnect();
      this.processorNode = null;
    }
    if (this.sourceNode) {
      this.sourceNode.disconnect();
      this.sourceNode = null;
    }
    if (this.mediaStream) {
      this.mediaStream.getTracks().forEach((track) => track.stop());
      this.mediaStream = null;
    }
    if (this.audioContext) {
      this.audioContext.close().catch(() => {});
      this.audioContext = null;
    }
    this.nextPlayTime = 0;
  }

  playAudioChunk(pcmData: ArrayBuffer, sampleRate = 24000): void {
    try {
      const AudioCtx = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      if (!this.audioContext || this.audioContext.state === 'closed') {
        this.audioContext = new AudioCtx({ sampleRate });
      }

      const int16Array = new Int16Array(pcmData);
      const float32Array = new Float32Array(int16Array.length);
      for (let i = 0; i < int16Array.length; i++) {
        float32Array[i] = int16Array[i] / 32768.0;
      }

      const buffer = this.audioContext.createBuffer(1, float32Array.length, sampleRate);
      buffer.getChannelData(0).set(float32Array);

      const source = this.audioContext.createBufferSource();
      source.buffer = buffer;
      source.connect(this.audioContext.destination);

      const currentTime = this.audioContext.currentTime;
      const startTime = Math.max(currentTime, this.nextPlayTime);
      source.start(startTime);
      this.nextPlayTime = startTime + buffer.duration;
    } catch {
      // Ignore playback errors on unsupported environments
    }
  }

  private floatTo16BitPCM(input: Float32Array): ArrayBuffer {
    const buffer = new ArrayBuffer(input.length * 2);
    const view = new DataView(buffer);
    for (let i = 0; i < input.length; i++) {
      const s = Math.max(-1, Math.min(1, input[i]));
      view.setInt16(i * 2, s < 0 ? s * 0x8000 : s * 0x7fff, true);
    }
    return buffer;
  }
}
