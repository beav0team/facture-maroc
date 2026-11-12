// The "Exporter mes données" file: what it contains, what it is called, and the
// batching that keeps a shop with thousands of documents from building a URL
// PostgREST refuses.

import { describe, expect, it } from 'vitest'
import { assemblerExport, erreurExport, lots, nomFichierExport, NOTE_EXPORT } from '../src/lib/export'
import type { Boutique } from '../src/lib/session.svelte'

const boutique = {
  id: 'b1',
  nom: 'Atelier Atlas',
  montant_lettres: true,
} as unknown as Boutique

const sources = {
  boutique,
  clients: [{ id: 'c1' }],
  articles: [],
  documents: [{ id: 'd1' }],
  lignes: [{ id: 'l1' }],
  paiements: [],
  compteurs: {},
}

describe('export des données', () => {
  it('produit un objet versionné, daté et lisible', () => {
    const sortie = assemblerExport(sources, new Date('2026-11-12T10:00:00Z'))
    expect(sortie.format).toBe('export')
    expect(sortie.version).toBe(1)
    expect(sortie.exporte_le).toBe('2026-11-12T10:00:00.000Z')
    expect(sortie.documents).toHaveLength(1)
    expect(sortie.note).toBe(NOTE_EXPORT)
  })

  it('nomme le fichier d’après la boutique et le jour, sans accent', () => {
    expect(nomFichierExport('Atelier Atlas', new Date('2026-11-12T10:00:00Z'))).toBe(
      'factureapp-atelier-atlas-2026-11-12.json',
    )
    expect(nomFichierExport('Épicerie  Chraïbi', new Date('2026-11-12T10:00:00Z'))).toBe(
      'factureapp-epicerie-chraibi-2026-11-12.json',
    )
    expect(nomFichierExport('   ', new Date('2026-11-12T10:00:00Z'))).toBe(
      'factureapp-boutique-2026-11-12.json',
    )
  })

  it('refuse un export dont les lignes manquent', () => {
    expect(erreurExport({ ...sources, lignes: [] })).toContain('lignes manquent')
    expect(erreurExport(sources)).toBeNull()
    // A shop with no document has no lines either: that is not a failure.
    expect(erreurExport({ ...sources, documents: [], lignes: [] })).toBeNull()
  })

  it('découpe les identifiants en lots, sans rien perdre', () => {
    const ids = Array.from({ length: 401 }, (_, i) => `d${i}`)
    const paquets = lots(ids)
    expect(paquets).toHaveLength(3)
    expect(paquets.flat()).toEqual(ids)
    expect(paquets[0]).toHaveLength(150)
    expect(lots([])).toEqual([])
  })
})
