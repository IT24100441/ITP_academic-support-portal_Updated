import React, { useEffect, useMemo, useState } from 'react';
import {
  Thermometer, Users, Cpu, Monitor, Sun, Tv,
  Wind, Volume2, Smile, Activity
} from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import { studySpotApi } from '../../api/studySpotApi';
import { environmentApi } from '../../api/environmentApi';

const humanizeStatus = (value) => (value || '').replaceAll('_', ' ');

const getTempBadge = (status) => {
  if (status === 'COOL') return 'bg-blue-50 text-blue-700 border-blue-200';
  if (status === 'WARM') return 'bg-orange-50 text-orange-700 border-orange-200';
  return 'bg-emerald-50 text-emerald-700 border-emerald-200';
};

const getOccupancyBadge = (status) => {
  if (status === 'FULL' || status === 'NEARLY_FULL' || status === 'NEARLY FULL') return 'bg-red-50 text-red-700 border-red-200';
  if (status === 'ACTIVE') return 'bg-amber-50 text-amber-700 border-amber-200';
  return 'bg-emerald-50 text-emerald-700 border-emerald-200';
};

// ─── Prediction Engine ────────────────────────────────────────────────────────
// Physics-inspired model based on ASHRAE thermal comfort standards:
//   • Each person  → +0.18 °C body heat, +30 ppm CO₂ exhaled, +3.5 dB noise
//   • Each PC      → +0.12 °C (heat dissipation), +0.8 dB fan noise
//   • Projector    → +0.9 °C (lamp heat), +6 dB projection fan
//   • Lighting     → up to +0.4 °C (LED at 100%)
const predictEnvironment = ({ people, computers, projector, lights, roomCapacity = 20 }) => {
  const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));
  const temp  = 22.0 + people * 0.18 + computers * 0.12 + (projector ? 0.9 : 0) + (lights / 100) * 0.4;
  const co2   = 420  + people * 30;
  const noise = 30   + people * 3.5  + computers * 0.8  + (projector ? 6 : 0);

  // Score each metric (0-100) then blend into a comfort index
  const tScore = temp  <= 22 ? 100 : temp  > 26 ? clamp(100 - (temp  - 26) * 12, 0, 100) : 100;
  const cScore = co2   <= 800 ? 100 : clamp(100 - (co2  - 800) * 0.08, 0, 100);
  const nScore = noise <= 50  ? 100 : clamp(100 - (noise - 50)  * 2.5,  0, 100);
  const oScore = roomCapacity > 0 ? clamp(100 - (people / roomCapacity) * 60, 0, 100) : 100;
  const comfort = Math.round(tScore * 0.4 + cScore * 0.3 + nScore * 0.2 + oScore * 0.1);

  return {
    temp:        +temp.toFixed(1),
    co2:         Math.round(co2),
    noise:       Math.round(noise),
    comfort:     clamp(comfort, 0, 100),
    tempStatus:  temp  < 21 ? 'COOL' : temp > 27 ? 'WARM' : 'OPTIMAL',
    co2Status:   co2   < 600 ? 'FRESH'   : co2   < 1000 ? 'MODERATE' : 'STUFFY',
    noiseStatus: noise < 45  ? 'QUIET'   : noise < 65   ? 'MODERATE' : 'LOUD',
  };
};

// ─── Slider Input ─────────────────────────────────────────────────────────────
const SliderInput = ({ icon: Icon, label, value, min, max, step = 1, unit, onChange, color = 'text-slate-400', disabled = false }) => (
  <div className={`space-y-1.5 ${disabled ? 'opacity-50' : ''}`}>
    <div className="flex items-center justify-between">
      <span className="flex items-center gap-2 text-sm font-semibold text-slate-700">
        <Icon size={15} className={color} />{label}
      </span>
      <span className="text-sm font-bold text-slate-900 tabular-nums min-w-[4rem] text-right">{value}{unit}</span>
    </div>
    <input
      type="range" min={min} max={max} step={step} value={value}
      onChange={e => onChange(+e.target.value)}
      disabled={disabled}
      title={disabled ? 'Admin access required' : undefined}
      className={`w-full h-2 rounded-full appearance-none bg-slate-100
        ${disabled ? 'cursor-not-allowed' : 'cursor-pointer accent-indigo-600'}`}
    />
    <div className="flex justify-between text-[10px] text-slate-400 font-medium">
      <span>{min}{unit}</span><span>{max}{unit}</span>
    </div>
  </div>
);

// ─── Mini Temperature Sparkline ───────────────────────────────────────────────
const MiniSparkline = ({ data }) => {
  if (data.length < 2) return null;
  const lo = Math.min(...data) - 0.5, hi = Math.max(...data) + 0.5, range = hi - lo || 1;
  const W = 280, H = 40;
  const pts = data.map((v, i) =>
    `${(i / (data.length - 1)) * W},${H - ((v - lo) / range) * (H - 10) - 5}`
  ).join(' ');
  return (
    <div className="rounded-2xl border border-slate-100 bg-slate-50 p-3">
      <p className="text-[10px] font-black uppercase tracking-widest text-slate-400 mb-2">
        Temperature History (last {data.length} readings)
      </p>
      <svg viewBox={`0 0 ${W} ${H}`} className="w-full" style={{ height: H }}>
        <polyline fill="none" stroke="#6366f1" strokeWidth="2.5"
          strokeLinecap="round" strokeLinejoin="round" points={pts} />
        {data.map((v, i) => (
          <circle key={i}
            cx={(i / (data.length - 1)) * W}
            cy={H - ((v - lo) / range) * (H - 10) - 5}
            r={i === data.length - 1 ? 5 : 3} fill="#6366f1"
            opacity={i === data.length - 1 ? 1 : 0.5}
          />
        ))}
      </svg>
      <div className="flex justify-between text-[9px] text-slate-400 mt-1 font-medium">
        <span>← Earlier</span><span>Now →</span>
      </div>
    </div>
  );
};

// ─── IoT Room Simulator Panel ────────────────────────────────────────────────
const RoomSimulator = ({ isAdmin = false }) => {
  const [people,    setPeople]    = useState(5);
  const [computers, setComputers] = useState(3);
  const [lights,    setLights]    = useState(80);
  const [projector, setProjector] = useState(false);
  const [liveDrift, setLiveDrift] = useState(false);
  const [tempHistory, setTempHistory] = useState([22.0]);

  const result = useMemo(
    () => predictEnvironment({ people, computers, projector, lights, roomCapacity: 30 }),
    [people, computers, projector, lights]
  );

  // Grow the sparkline history whenever the predicted temp changes
  useEffect(() => {
    setTempHistory(prev => [...prev.slice(-11), result.temp]);
  }, [result.temp]);

  // Live Drift: gently nudge people/computers every 2s to mimic real sensor noise
  useEffect(() => {
    if (!liveDrift) return;
    const id = setInterval(() => {
      setPeople(p  => Math.max(0, Math.min(30, p  + (Math.random() > 0.5 ? 1 : -1))));
      setComputers(c => Math.max(0, Math.min(20, c + (Math.random() > 0.6 ? 1 : -1))));
    }, 2000);
    return () => clearInterval(id);
  }, [liveDrift]);

  const comfortColor = result.comfort >= 75 ? 'text-emerald-600' : result.comfort >= 50 ? 'text-amber-500' : 'text-red-500';
  const comfortBg    = result.comfort >= 75 ? 'bg-emerald-50 border-emerald-200' : result.comfort >= 50 ? 'bg-amber-50 border-amber-200' : 'bg-red-50 border-red-200';
  const co2Cls       = result.co2Status === 'STUFFY'   ? 'bg-orange-50 border-orange-200 text-orange-700'
                     : result.co2Status === 'MODERATE' ? 'bg-amber-50  border-amber-200  text-amber-700'
                     :                                   'bg-teal-50   border-teal-200   text-teal-700';
  const noiseCls     = result.noiseStatus === 'LOUD'     ? 'bg-red-50    border-red-200    text-red-700'
                     : result.noiseStatus === 'MODERATE' ? 'bg-yellow-50 border-yellow-200 text-yellow-700'
                     :                                     'bg-sky-50    border-sky-200    text-sky-700';

  const presets = [
    { label: '🏠 Empty Room',     people: 0,  computers: 0,  lights: 20,  projector: false },
    { label: '📚 Study Group',    people: 8,  computers: 8,  lights: 80,  projector: false },
    { label: '🎓 Lecture Class',  people: 25, computers: 1,  lights: 100, projector: true  },
    { label: '🔥 Full Capacity',  people: 30, computers: 20, lights: 100, projector: true  },
  ];

  const tips = [];
  if (result.tempStatus === 'WARM') tips.push('🌀 Turn on air conditioning — room temperature is above comfort range.');
  if (result.tempStatus === 'COOL') tips.push('🔆 Consider enabling heating for better occupant comfort.');
  if (result.co2Status === 'STUFFY') tips.push('🪟 Open windows or increase ventilation — CO₂ is dangerously high.');
  else if (result.co2Status === 'MODERATE') tips.push('💨 CO₂ is rising — consider improving airflow.');
  if (result.noiseStatus === 'LOUD') tips.push('🔇 Noise levels are high — enforce quiet policies or add soundproofing.');
  if (result.comfort >= 85) tips.push('✅ Room conditions are excellent! No action required.');

  return (
    <div className="rounded-3xl border-2 border-indigo-100 bg-gradient-to-br from-indigo-50/60 to-white p-6 shadow-sm">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-3 mb-5">
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-2xl bg-indigo-600 text-white shadow">
            <Cpu size={20} />
          </div>
          <div>
            <h3 className="text-lg font-bold text-slate-900">IoT Room Simulator</h3>
            <p className="text-xs text-slate-500">Predict live environmental conditions from room parameters</p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          {/* Role badge */}
          {isAdmin ? (
            <span className="flex items-center gap-1 rounded-full border border-indigo-200 bg-indigo-50 px-3 py-1 text-[10px] font-black uppercase tracking-widest text-indigo-700">
              🛡️ Admin Controls
            </span>
          ) : (
            <span className="flex items-center gap-1 rounded-full border border-slate-200 bg-slate-100 px-3 py-1 text-[10px] font-black uppercase tracking-widest text-slate-500">
              👁️ View Only
            </span>
          )}
          {/* Live Drift toggle — admin only */}
          <span className={`text-xs font-semibold ${isAdmin ? 'text-slate-600' : 'text-slate-300'}`}>Live Drift</span>
          <button
            onClick={() => isAdmin && setLiveDrift(v => !v)}
            disabled={!isAdmin}
            title={isAdmin ? 'Toggle live drift simulation' : 'Admin access required'}
            className={`relative h-6 w-11 rounded-full transition-colors duration-300
              ${liveDrift && isAdmin ? 'bg-indigo-600' : 'bg-slate-200'}
              ${!isAdmin ? 'cursor-not-allowed opacity-40' : 'cursor-pointer'}`}>
            <span className={`absolute top-1 h-4 w-4 rounded-full bg-white shadow transition-all duration-300 ${liveDrift && isAdmin ? 'left-6' : 'left-1'}`} />
          </button>
          {liveDrift && isAdmin && (
            <span className="flex items-center gap-1 text-xs font-bold text-indigo-600 animate-pulse">
              <Activity size={11} /> Simulating...
            </span>
          )}
        </div>
      </div>

      {/* View-only notice for non-admins */}
      {!isAdmin && (
        <div className="mb-5 flex items-start gap-3 rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3">
          <span className="mt-0.5 text-base">🔒</span>
          <div>
            <p className="text-sm font-bold text-amber-800">View-only mode</p>
            <p className="text-xs text-amber-700 mt-0.5">Only administrators can change simulator parameters. You can observe the predicted readings below.</p>
          </div>
        </div>
      )}

      {/* Quick presets */}
      <div className="flex flex-wrap gap-2 mb-6">
        {presets.map(p => (
          <button key={p.label}
            onClick={() => { if (isAdmin) { setPeople(p.people); setComputers(p.computers); setLights(p.lights); setProjector(p.projector); } }}
            disabled={!isAdmin}
            title={isAdmin ? `Load "${p.label}" preset` : 'Admin access required'}
            className={`rounded-full border px-3 py-1 text-xs font-semibold shadow-sm transition-colors
              ${isAdmin
                ? 'border-indigo-200 bg-white text-indigo-700 hover:bg-indigo-50 cursor-pointer'
                : 'border-slate-200 bg-slate-50 text-slate-400 cursor-not-allowed opacity-60'}`}>
            {p.label}
          </button>
        ))}
      </div>

      {/* Two-column layout: inputs | outputs */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        {/* ── Inputs ── */}
        <div className={`space-y-5 rounded-2xl border bg-white p-5 shadow-sm ${isAdmin ? 'border-slate-100' : 'border-slate-100 opacity-75'}`}>
          <p className="text-[10px] font-black uppercase tracking-widest text-slate-400">
            Room Parameters {!isAdmin && <span className="ml-1 text-slate-300">(read-only)</span>}
          </p>
          <SliderInput icon={Users}   label="People in Room"   value={people}    min={0} max={30}  unit=" ppl" onChange={v => isAdmin && setPeople(v)}    color="text-violet-500" disabled={!isAdmin} />
          <SliderInput icon={Monitor} label="Active Computers" value={computers} min={0} max={20}  unit=" pcs" onChange={v => isAdmin && setComputers(v)}  color="text-blue-500"   disabled={!isAdmin} />
          <SliderInput icon={Sun}     label="Lighting Level"   value={lights}    min={0} max={100} unit="%"    onChange={v => isAdmin && setLights(v)}     color="text-amber-400"  disabled={!isAdmin} />
          <div className="flex items-center justify-between pt-1">
            <span className={`flex items-center gap-2 text-sm font-semibold ${isAdmin ? 'text-slate-700' : 'text-slate-400'}`}>
              <Tv size={15} className={isAdmin ? 'text-rose-400' : 'text-slate-300'} /> Projector
            </span>
            <button
              onClick={() => isAdmin && setProjector(v => !v)}
              disabled={!isAdmin}
              title={isAdmin ? 'Toggle projector' : 'Admin access required'}
              className={`relative h-6 w-11 rounded-full transition-colors duration-300
                ${projector && isAdmin ? 'bg-rose-500' : 'bg-slate-200'}
                ${!isAdmin ? 'cursor-not-allowed opacity-40' : 'cursor-pointer'}`}>
              <span className={`absolute top-1 h-4 w-4 rounded-full bg-white shadow transition-all duration-300 ${projector && isAdmin ? 'left-6' : 'left-1'}`} />
            </button>
          </div>
        </div>

        {/* ── Outputs ── */}
        <div className="space-y-4">
          <p className="text-[10px] font-black uppercase tracking-widest text-slate-400">Predicted Readings</p>
          <div className="grid grid-cols-2 gap-3">
            <div className={`rounded-2xl border p-4 ${getTempBadge(result.tempStatus)}`}>
              <div className="flex items-center gap-1.5 mb-1"><Thermometer size={14} /><span className="text-[10px] font-black uppercase tracking-wider">Temp</span></div>
              <p className="text-2xl font-extrabold tabular-nums">{result.temp}°C</p>
              <p className="text-[10px] font-semibold mt-1 opacity-80">{result.tempStatus}</p>
            </div>
            <div className={`rounded-2xl border p-4 ${co2Cls}`}>
              <div className="flex items-center gap-1.5 mb-1"><Wind size={14} /><span className="text-[10px] font-black uppercase tracking-wider">CO₂</span></div>
              <p className="text-2xl font-extrabold tabular-nums">{result.co2} ppm</p>
              <p className="text-[10px] font-semibold mt-1 opacity-80">{result.co2Status}</p>
            </div>
            <div className={`rounded-2xl border p-4 ${noiseCls}`}>
              <div className="flex items-center gap-1.5 mb-1"><Volume2 size={14} /><span className="text-[10px] font-black uppercase tracking-wider">Noise</span></div>
              <p className="text-2xl font-extrabold tabular-nums">{result.noise} dB</p>
              <p className="text-[10px] font-semibold mt-1 opacity-80">{result.noiseStatus}</p>
            </div>
            <div className={`rounded-2xl border p-4 ${comfortBg}`}>
              <div className={`flex items-center gap-1.5 mb-1 ${comfortColor}`}><Smile size={14} /><span className="text-[10px] font-black uppercase tracking-wider">Comfort</span></div>
              <p className={`text-2xl font-extrabold tabular-nums ${comfortColor}`}>{result.comfort}/100</p>
              <div className="mt-2 h-1.5 w-full rounded-full bg-white/70 overflow-hidden">
                <div className={`h-full rounded-full transition-all duration-700 ${result.comfort >= 75 ? 'bg-emerald-500' : result.comfort >= 50 ? 'bg-amber-400' : 'bg-red-500'}`}
                  style={{ width: `${result.comfort}%` }} />
              </div>
            </div>
          </div>
          <MiniSparkline data={tempHistory} />
        </div>
      </div>

      {/* Recommendations banner */}
      {tips.length > 0 && (
        <div className={`mt-5 rounded-2xl border p-4 ${result.comfort >= 75 ? 'bg-emerald-50 border-emerald-200' : result.comfort >= 50 ? 'bg-amber-50 border-amber-200' : 'bg-red-50 border-red-200'}`}>
          <p className="text-[10px] font-black uppercase tracking-widest text-slate-500 mb-2">Smart Recommendations</p>
          <ul className="space-y-1">{tips.map((t, i) => <li key={i} className="text-sm text-slate-700 font-medium">{t}</li>)}</ul>
        </div>
      )}
    </div>
  );
};

const EnvironmentPage = () => {
  const { user } = useAuth();
  const role = user?.role || (Array.isArray(user?.roles) ? user.roles[0] : null);
  const isAdmin = role === 'ADMIN';

  const [rooms, setRooms] = useState([]);
  const [dashboard, setDashboard] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const loadData = async (showLoader = false) => {
    if (showLoader) {
      setLoading(true);
    }
    try {
      const [roomData, dashboardData] = await Promise.all([
        studySpotApi.getRooms(),
        environmentApi.getDashboardEnvironment()
      ]);
      setRooms(Array.isArray(roomData) ? roomData : []);
      setDashboard(dashboardData || null);
      setError('');
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to load environment data.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData(true);
    const intervalId = setInterval(() => loadData(false), 5000);
    return () => clearInterval(intervalId);
  }, []);

  const aggregate = useMemo(() => {
    if (!rooms.length) {
      return {
        avgTemp: dashboard?.averageTemperature ?? 0,
        occupancyPercent: 0,
        occupancyStatus: 'AVAILABLE',
        temperatureStatus: 'OPTIMAL'
      };
    }

    const avgTemp = rooms.reduce((sum, room) => sum + (room.temperature ?? 0), 0) / rooms.length;
    const totalCapacity = rooms.reduce((sum, room) => sum + (room.capacity ?? 0), 0);
    const totalOccupancy = rooms.reduce((sum, room) => sum + (room.currentOccupancy ?? 0), 0);
    const occupancyPercent = totalCapacity > 0 ? (totalOccupancy * 100) / totalCapacity : 0;

    const occupancyStatus = occupancyPercent === 0
      ? 'AVAILABLE'
      : occupancyPercent >= 100
        ? 'FULL'
        : occupancyPercent > 70
          ? 'NEARLY_FULL'
          : 'ACTIVE';

    const temperatureStatus = avgTemp < 22 ? 'COOL' : avgTemp > 28 ? 'WARM' : 'OPTIMAL';

    return { avgTemp, occupancyPercent, occupancyStatus, temperatureStatus };
  }, [rooms, dashboard]);

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-3xl font-bold text-slate-900">Environment Monitor</h1>
          <p className="text-slate-500">Temperature and occupancy telemetry across all study halls.</p>
        </div>
        <div className="text-xs font-medium text-slate-500">Auto-sync: 5s</div>
      </div>

      {error && (
        <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>
      )}

      {loading ? (
        <div className="rounded-2xl border border-slate-200 bg-white p-10 text-center text-slate-500">Loading environment monitor...</div>
      ) : (
        <>
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            <div className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm transition-all hover:shadow-lg">
              <div className="mb-4 flex items-center justify-between">
                <div className="flex items-center gap-2 text-slate-700">
                  <Thermometer size={20} />
                  <h2 className="text-lg font-semibold">Temperature</h2>
                </div>
                <span className={`rounded-full border px-3 py-1 text-xs font-bold ${getTempBadge(aggregate.temperatureStatus)}`}>
                  {aggregate.temperatureStatus}
                </span>
              </div>
              <p className="text-4xl font-bold text-slate-900">{aggregate.avgTemp.toFixed(1)} C</p>
              <p className="mt-2 text-sm text-slate-500">Campus average from all synced study rooms.</p>
            </div>

            <div className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm transition-all hover:shadow-lg">
              <div className="mb-4 flex items-center justify-between">
                <div className="flex items-center gap-2 text-slate-700">
                  <Users size={20} />
                  <h2 className="text-lg font-semibold">Occupancy</h2>
                </div>
                <span className={`rounded-full border px-3 py-1 text-xs font-bold ${getOccupancyBadge(aggregate.occupancyStatus)}`}>
                  {humanizeStatus(aggregate.occupancyStatus)}
                </span>
              </div>
              <p className="text-4xl font-bold text-slate-900">{Math.round(aggregate.occupancyPercent)}%</p>
              <p className="mt-2 text-sm text-slate-500">
                Total occupancy: {dashboard?.totalOccupancy ?? rooms.reduce((acc, room) => acc + (room.currentOccupancy ?? 0), 0)}
              </p>
            </div>
          </div>

          <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
            <h3 className="mb-4 text-lg font-semibold text-slate-900">Room Status Indicators</h3>
            <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
              {rooms.map((room) => (
                <div key={room.id} className="flex items-center justify-between rounded-xl border border-slate-100 bg-slate-50 px-4 py-3">
                  <div>
                    <p className="font-semibold text-slate-900">{room.name || room.roomName}</p>
                    <p className="text-xs text-slate-500">{room.building} | Floor {room.floor}</p>
                  </div>
                  <div className="text-right">
                    <span className={`inline-block rounded-full border px-2 py-1 text-[10px] font-bold ${getOccupancyBadge(room.status)}`}>
                      {humanizeStatus(room.status)}
                    </span>
                    <p className="mt-1 text-xs text-slate-500">{Math.round(room.occupancyPercent ?? 0)}%</p>
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* ── IoT Simulator (Demo) ── */}
          <RoomSimulator isAdmin={isAdmin} />
        </>
      )}
    </div>
  );
};

export default EnvironmentPage;
