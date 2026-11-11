-- FactureApp — 0004 : import d'une sauvegarde Android
--
-- À exécuter après 0003. Idempotent.
--
-- Le seul geste que le client ne peut pas faire lui-même est d'écrire dans
-- `numerotation` : la table est en lecture seule pour les boutiques. Cette
-- fonction reprend les compteurs d'une sauvegarde Android pour que la
-- numérotation continue au lieu de repartir à 0001 et de réémettre un numéro
-- déjà imprimé sur du papier.

create or replace function public.importer_compteurs(p_shop uuid, p_compteurs jsonb)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  v_type   text;
  v_valeur integer;
  v_annee  integer := extract(year from current_date)::integer;
  v_total  integer := 0;
begin
  if auth.uid() is null then
    raise exception 'authentification requise' using errcode = '28000';
  end if;
  if not public.is_shop_member(p_shop) then
    raise exception 'boutique inconnue' using errcode = '42501';
  end if;

  for v_type, v_valeur in
    select e.key, e.value::integer
    from jsonb_each_text(coalesce(p_compteurs, '{}'::jsonb)) as e
    where e.value ~ '^[0-9]+$'
      and length(e.value) <= 9
      and e.key in (
        'facture', 'devis', 'bon_commande', 'ticket', 'avoir',
        'proforma', 'acompte', 'regul', 'simplifiee', 'export'
      )
  loop
    if v_valeur < 1 then
      continue;
    end if;

    insert into public.numerotation (shop_id, type, annee, prochaine_valeur)
    values (p_shop, v_type, v_annee, v_valeur)
    on conflict (shop_id, type) do update
      set annee = v_annee,
          -- Never go backwards: a shop that already issued on the web must
          -- not reuse a number because an older backup was imported.
          prochaine_valeur = greatest(numerotation.prochaine_valeur, excluded.prochaine_valeur);

    v_total := v_total + 1;
  end loop;

  return v_total;
end;
$$;

revoke all on function public.importer_compteurs(uuid, jsonb) from public, anon;
grant execute on function public.importer_compteurs(uuid, jsonb) to authenticated;
