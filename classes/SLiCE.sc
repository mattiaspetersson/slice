SLiCE {
	var pathToSounds;
	var server, serverOptions, tempoClock;
	var synthGroup, masterBus, reverbBus, masterSynth, buffers;
	var midiOuts;
	var waveshaperBuffers;
	var currentlyLoadedSynthDefs;
	classvar instance;

	*new {|pathToSounds|
		if(instance.isNil) { // run as a singleton
			^instance = super.newCopyArgs(pathToSounds).init;
		} { "There can be only one SLiCE!".warn; ^instance; };
	}

	init {
		MIDIClient.init;
		server = Server.default;
		serverOptions = server.options;
		this.setServerOptions(serverOptions);
		tempoClock = TempoClock.default;
		buffers = Dictionary[];
		midiOuts = Dictionary[];
		currentlyLoadedSynthDefs = List[];

		server.waitForBoot{
			synthGroup = Group(server);
			masterBus = Bus.audio(server, 2);
			reverbBus = Bus.audio(server, 2);

			/*pathToSounds = pathToSounds ? PathName(thisProcess.nowExecutingPath).pathOnly ++ "sounds";
			PathName(pathToSounds).entries.do{|file|
				var sf = SoundFile.new;
				if(sf.openRead( file.fullPath )) {
					sf.close;
					buffers[file.fileNameWithoutExtension.asSymbol] = Buffer.read(server, file.fullPath);
				};
			};*/

			if(pathToSounds.isKindOf(String)) {
				this.addSound(nil, pathToSounds);
			};

			server.sync;

			// fill waveshaperbuffers
			waveshaperBuffers = [
				[1.0, 0.5, 0.33333333333333, 0.25, 0.2, 0.16666666666667, 0.14285714285714, 0.125],
				[0.33333333333333, 0.2, 0.14285714285714, 0.11111111111111, 0.5, 0.25, 0.16666666666667],
				[0.125, 0.33333333333333, 0.14285714285714, 0.5, 0.16666666666667, 1.0],
				[0.5, 1.0, 0.11111111111111, 0.125, 0.14285714285714],
				[0.33333333333333, 1.0, 0.125, 0.16666666666667],
				[0.25, 0.11111111111111, 0.2],
				[0.25, 0.2],
				[0.11111111111111]
			].collect{|c|
				Buffer.alloc(server, 1024, 1, {|buf| buf.chebyMsg(c)});
			};

			server.sync;

			SLiCE.synthFuncs.keysValuesDo{|key, val|
				SLiCE.buildSynthDefs(key, val[0], val[1]);
				currentlyLoadedSynthDefs.add(key);
			};

			server.sync;

			SynthDef(\master, {|in, revIn|
				var sig, rev, input, reverbInput, satVal;
				input = In.ar(in, 2);
				reverbInput = In.ar(revIn, 2);
				rev = NHHall.ar(reverbInput,
					\revTime.kr(1.8).clip(0.1, inf),
					\revStereo.kr(0.5).clip(0.0, 1.0),
					\revLoFreq.kr(200).clip(20, 20000),
					\revLoRatio.kr(0.5).clip(0.0, 1.0),
					\revHiFreq.kr(4000).clip(20, 20000),
					\revHiRatio.kr(0.5).clip(0.0, 1.0),
					\revEarlyDiffusion.kr(0.5).clip(0.0, 1.0),
					\revLateDiffusion.kr(0.5).clip(0.0, 1.0),
					\revModRate.kr(0.2).clip(0.0, 1.0),
					\revModDepth.kr(0.3).clip(0.0, 1.0)
				);
				sig = input + rev;
				satVal = (\saturation.kr(1.0).lag(0.1)).clip(1.0, 99.0);
				sig = (sig * satVal).tanh / (satVal ** 0.6); // formula by James Harkins (satVal can't be 0!)
				Out.ar(0, Limiter.ar(sig));
			}).add;

			server.sync;

			masterSynth = Synth.after(synthGroup, \master, [\in, masterBus, \revIn, reverbBus]);

			server.sync;

			Event.addParentType(\note, (
				finish: {
					currentEnvironment[\prob] = ~prob ? 1.0;
					currentEnvironment[\div] = ~div ? 1;
					currentEnvironment[\group] = synthGroup;
					currentEnvironment[\out] = masterBus;
					currentEnvironment[\reverbOut] = reverbBus;
					currentEnvironment[\beatDur] = { tempoClock.beatDur };
					currentEnvironment[\eventDur] = ~dur ? 1.0;

					if(~shaperBufA.notNil) {
						currentEnvironment[\shaperBufA] = waveshaperBuffers[~shaperBufA % waveshaperBuffers.size];
					} {
						currentEnvironment[\shaperBufA] = waveshaperBuffers[0];
					};
					if(~shaperBufB.notNil) {
						currentEnvironment[\shaperBufB] = waveshaperBuffers[~shaperBufB % waveshaperBuffers.size];
					} {
						currentEnvironment[\shaperBufB] = waveshaperBuffers[1];
					};

					// check probability and select sounds
					if([\note, \rest].wchoose(
						[currentEnvironment[\prob], 1 - currentEnvironment[\prob]]) == \note
					) {
						/*if(buffers.includesKey(~sound)) {
							currentEnvironment[\buf] = buffers[~sound].bufnum
						};

						if(buffers.includesKey(~sound)) {
							case { buffers[~sound].numChannels == 1 } {
								currentEnvironment[\instrument] = \samplePlayerMono
							};
							case { buffers[~sound].numChannels == 2 } {
								currentEnvironment[\instrument] = \samplePlayerStereo
							};
						} {
							if(SynthDescLib.global[~sound].notNil)
							{ currentEnvironment[\instrument] = ~sound }
							{ currentEnvironment[\type] = \rest }
						};*/

						// rewrite the above as a switch?
						var snd, midiOut;
						snd = buffers[~sound] ? SynthDescLib.global[~sound] ? midiOuts[~sound];
						switch(snd.class,
							Buffer, {
								// set the \buf key
								currentEnvironment[\buf] = buffers[~sound].bufnum;

								// check numChannels and select \instrument
								case { buffers[~sound].numChannels == 1 } {
									currentEnvironment[\instrument] = \samplePlayerMono
								};
								case { buffers[~sound].numChannels == 2 } {
									currentEnvironment[\instrument] = \samplePlayerStereo
								};
							},

							SynthDesc, {
								currentEnvironment[\instrument] = ~sound;
							},

							MIDIOut, {
								currentEnvironment[\type] = \midi;
								currentEnvironment[\midiout] = midiOuts[~sound];
								currentEnvironment[\chan] = ~chan ? 0;
							},

							// if nothing matches set \type to \rest
							{
								currentEnvironment[\type] = \rest
							}
						);
					} { currentEnvironment[\type] = \rest };

					//currentEnvironment.postln;
					//currentEnvironment[\dur] = Psubdivide(~div ? 1, ~dur ? 0.25);
				}
			));
		};
	}

	setServerOptions {|aServerOptions|
		var o = aServerOptions, safetyClipThreshold;
		var memSize, numBuffers, numWireBufs, maxSynthDefs, numAudioBusChannels, numControlBusChannels;

		memSize = 2097152; // 2 GB
		numBuffers = 8192;
		numWireBufs = 32768;
		maxSynthDefs = 8192;
		numAudioBusChannels = 8192;
		numControlBusChannels = 131072;

		// ha värdena i en Dictionary istället

/*[\memSize, \numBuffers, \numWireBufs, \maxSynthDefs, \numAudioBusChannels, \numControlBusChannels].do{|m|
			if(o.perform(m) < m.asString.interpret) {
				o.perform((m++"_").asSymbol, m.asString.interpret);
			};
		};*/

		if(o.memSize < memSize) {o.memSize = memSize};
		if(o.numBuffers < numBuffers) {o.numBuffers = numBuffers};
		if(o.numWireBufs < numWireBufs) {o.numWireBufs = numWireBufs};
		if(o.maxSynthDefs < maxSynthDefs) {o.maxSynthDefs = maxSynthDefs};
		if(o.numAudioBusChannels < numAudioBusChannels) {o.numAudioBusChannels = numAudioBusChannels};
		if(o.numControlBusChannels < numControlBusChannels) {o.numControlBusChannels = numControlBusChannels};

		o.safetyClipThreshold = 1.0;
		o.loadDefs = false;

		^o;
	}

	bpm {|bpm = 126|
		tempoClock.tempo = bpm/60;
	}

	addSound {|name, sound, format = \mono|
		var sf = SoundFile.new, n;

		// check names!

		sound.class.switch(
			String, {
				case(
					{PathName(sound).isFile},
					{
						var file = PathName(sound);
						if(sf.openRead(file.fullPath)) {
							sf.close;
							n = name ? file.fileNameWithoutExtension.asSymbol;
							buffers[n] = Buffer.read(server, file.fullPath);
						};
					},

					{PathName(sound).isFolder},
					{
						PathName(sound).entries.do{|file|
							if(sf.openRead(file.fullPath)) {
								sf.close;
								if(name.notNil) {
									n = (name ++ "-" ++ file.fileNameWithoutExtension).asSymbol;
									buffers[n] = Buffer.read(server, file.fullPath);
								}{
									n = file.fileNameWithoutExtension.asSymbol;
									buffers[n] = Buffer.read(server, file.fullPath);
								};
							};
						};
					}
				);
			},

			Function, {
				// the passed in function have the following prependArgs:
				// freq, gate, atk, rel, buf, loop, beatDur, eventDur
				// add your own parameters using Named Control syntax
				SLiCE.buildSynthDefs(name, sound, format);
				currentlyLoadedSynthDefs.add(name); // not very safe! Should only happen if the above succeeded.
			},

			MIDIOut, {
				midiOuts[name] = sound;
			},

			{
				"Sound must be either
• a valid path to a soundfile,
• a path to a folder of soundfiles, or,
• a synth function following the format:
{|freq, gate, atk, rel, buf, loop, ... additionalArgs ...|
SinOscFB.ar(freq, fb, 0.2);
}".postln;
			};
		);
	}

	listSounds {
		buffers.keysDo{|name|
			("\% (sample)").format(name).postln;
		};

		currentlyLoadedSynthDefs.do{|name|
			if(name != \samplePlayerMono and: {name != \samplePlayerStereo}) {
				("\% (synth)").format(name).postln;
			}
		};
	}

	listParams {

	}

	master {|par, val|
		server.bind{masterSynth.set(par, val)};
	}

	*synthFuncs {
		// these are the default synthDef functions to be loaded at startup
		// add your own on the fly using the addSound method or add them here
		^(
			sine: [
				{|freq, gate, atk, rel, buf, loop|
					SinOsc.ar(freq + SinOsc.ar(\fmFreq.kr(0), 0, \fmAmt.kr(0)), 0, 0.2);
				},
				\mono
			],

			saw: [
				{|freq, gate, atk, rel, buf, loop|
					Saw.ar(freq + SinOsc.ar(\fmFreq.kr(3), 0, \fmAmt.kr(0)), 0.2);
				}, \mono
			],

			pulse: [
				{|freq, gate, atk, rel, buf, loop|
					Pulse.ar(
						freq + SinOsc.ar(\fmFreq.kr(3), 0, \fmAmt.kr(0)),
						\pulseWidth.kr(0.5).clip(0.01, 0.99),
						0.2
					);
				}, \mono
			],

			triangle: [
				{|freq, gate, atk, rel, buf, loop|
					VarSaw.ar(freq + SinOsc.ar(\fmFreq.kr(3), 0, \fmAmt.kr(0)),
						0.0,
						\pulseWidth.kr(0.5).clip(0.01, 0.99),
						0.2
					);
				}, \mono
			],

			whiteNoise: [
				{|freq, gate, atk, rel, buf, loop|
					WhiteNoise.ar(0.2);
				},
				\mono
			],

			pinkNoise: [
				{|freq, gate, atk, rel, buf, loop|
					PinkNoise.ar(0.2);
				},
				\mono
			],

			brownNoise: [
				{|freq, gate, atk, rel, buf, loop|
					BrownNoise.ar(0.2);
				},
				\mono
			],

			samplePlayerMono: [
				{|freq, gate, atk, rel, buf, loop|
					var sig, r = (freq.cpsmidi - 60).midiratio * BufRateScale.kr(buf);
					var reverse = \reverse.kr(0);

					sig = PlayBuf.ar(
						1,
						buf,
						Select.ar(reverse, [r, r * -1]),
						startPos: (BufFrames.kr(buf)-2) * reverse,
						loop: loop
					);
					sig;
				}, \mono
			],

			samplePlayerStereo: [
				{|freq, gate, atk, rel, buf, loop|
					var sig, r = (freq.cpsmidi - 60).midiratio * BufRateScale.kr(buf);
					var reverse = \reverse.kr(0);

					sig = PlayBuf.ar(
						2,
						buf,
						Select.ar(reverse, [r, r * -1]),
						startPos: (BufFrames.kr(buf)-2) * reverse,
						loop: loop
					);
					sig;
				}, \stereo
			]
		);
	}

	*buildSynthDefs {|name, func, format = \mono|
		// the SynthDef to wrap other functions in
		SynthDef(name, {
			arg out, reverbOut, buf, freq, gate = 1, pan, amp, atk = 0, rel = 0.9, loop = 0,
			filter = 1, lpf = 16000, lpr = 0.1, hpf = 20, hpr = 0.1, saturation = 1.0,
			reverb = 0.0, delTime = 0.5, delFbTime = 3, delay = 0,
			vibrato = 0, vibratoRate = 6, tremolo = 0, tremoloRate = 4, beatDur = 1, eventDur = 1,
			shaper = 0, shaperBufA, shaperBufB, shaperMorph = 0, shaperWarp = 1;
			// note: the beatDur and eventDur should be reported by a function in the pattern!

			var g = gate, f = freq, a = atk, r = rel, b = buf, l = loop;
			var env, sig, pannedOut, satVal, ws;

			env = EnvGen.ar(Env.asr(atk, 1, rel), gate + Impulse.kr(0));

			// handle the doneAction separately due to the delay
			Line.kr(0, 1, beatDur * (eventDur + delTime + delFbTime), doneAction: 2);

			// vibrato / freq modulation
			f = f + SinOsc.ar(vibratoRate * beatDur.reciprocal, 0, vibrato);

			// wrap function in here -----------------------------------------------------
			sig = SynthDef.wrap(func,
				[\ar, \kr, \kr, \kr, \kr, \kr, \kr, \kr],
				[f, g, a, r, b, l, beatDur, eventDur]
			);
			// ---------------------------------------------------------------------------

			// tremolo / amplitude modulation
			sig = sig * XFade2.ar(
				DC.ar(1.0),
				SinOsc.ar(tremoloRate * beatDur.reciprocal).unipolar(tremolo.clip(0.0, 1.0)),
				(tremolo * 2) - 1
			);

			// waveshaping
			ws = XFade2.ar(
				Shaper.ar(shaperBufA, sig * shaperWarp.clip(1.0, 20.0)),
				Shaper.ar(shaperBufB, sig * shaperWarp.clip(1.0, 20.0)),
				shaperMorph.clip(-1.0, 1.0)
			);
			ws = LeakDC.ar(ws);
			sig = XFade2.ar(sig, ws, (shaper.clip(0.0, 1.0) * 2) - 1);

			// handle mono or stereo panning
			switch(format,
				\mono, {pannedOut = Pan2.ar(sig, pan)},
				\stereo, {
					pannedOut = Pan2.ar(
						sig[0],
						pan.linlin(0, 1, -1, 1 )
					) + Pan2.ar(
						sig[1],
						pan.linlin(-1, 0, -1, 1)
					)
				}
			);
			sig = pannedOut;

			// DJ style filter (hipass going into lopass)
			sig = Select.ar(filter, [
				sig,
				RLPF.ar(
					RHPF.ar(
						sig,
						(hpf.lag(0.01)).clip(20, 20000),
						(hpr.lag(0.01)).clip(0.0001, 1.0)
					),
					(lpf.lag(0.01)).clip(20, 20000),
					(lpr.lag(0.01)).clip(0.0001, 1.0)
				)
			]);

			// beat synced delay
			sig = XFade2.ar(
				sig,
				CombN.ar(
					sig,
					4,
					(beatDur * delTime).clip(0.001, 4),
					(beatDur * delFbTime).clip(0.0, inf)
			), delay.linlin(0, 1, -1, 1));

			// saturation
			satVal = (saturation.lag(0.1)).clip(1.0, 99.0);
			sig = (sig * satVal).tanh / (satVal ** 0.6); // formula by James Harkins (satVal can't be 0!)

			sig = sig * amp.clip(0.0, 1.0) * env;

			Out.ar(out, sig);
			Out.ar(reverbOut, sig * reverb.clip(0.0, 1.0));
		}).add;
	}
}