# SLiCE
SLiCE is a Simple Live Coding Environment, that provides a simplified framework for SuperCollider pattern interaction by adding useful default values and a streamlined SynthDef factory. It adds default values to Event's \note type; i.e., do not change the \type in the patterns you write within SLiCE.

The main difference to working with regular patterns and events is the added \sound key, that automatically sets other necessary event keys for various types of instruments. From a user perspective, one does not have think about whether the sound is a sample, a synth function, or an external MIDI instrument (although the latter is not yet implemented). Furthermore, everything is wrapped within a SynthDef providing waveshaping, beat-synced delay, resonant highpass and lowpass filters, beat-synced vibrato (FM) and tremolo (AM), saturation, and an auxiliary output for sending to a reverb in the master synth. Almost all values are clipped to stay within sane ranges and to avoid unintended sonic explosions. The master synth also contains a Limiter to further protect your ears.


## Example
```
l = SLiCE(); // enter SLiCE

(
l.bpm(72); // set the tempo in beats per minute
l.master(\revTime, 3); // set the reverb decay time (0.1 - inf)
l.master(\saturation, 4); // set the master saturation amount (1 - 99)

Pdef(\a,
    Pbind(
        \sound, \sine,
        \ctranspose, Prand([0, -12, 12], inf),
        \note, Prand([0, 3, 5, 8, 10], inf),
        \rel, 3,
        \atk, 0.66,
        \tremolo, 0.5,
        \tremoloRate, Pwhite(1, 8),
        \shaper, 1.0,
        \shaperBufA, 1,
        \shaperBufB, Pwhite(0, 7),
        \shaperMorph, Ndef(\morph, {SinOsc.ar(0.9)}),
        \shaperWarp, Pmeanrand(1.0, 8.0),
        \delay, 0.2,
        \reverb, 0.3,
        \amp, Pseq([0.2, 0.15, 0.1] * 0.8, inf),
        \dur, 0.25,
        \prob, 0.4
    )
).play;
)

Pdef(\a).stop;
```
